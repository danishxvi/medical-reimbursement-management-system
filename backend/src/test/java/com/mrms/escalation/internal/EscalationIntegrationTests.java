package com.mrms.escalation.internal;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deadlines with teeth, end to end: an e-NAC held by a pharmacist is
 * reminded, then breaches (supervisor and employee told, record put back in
 * the queue), then escalates to the zonal oversight officer, who can send
 * one reminder an hour. Each step happens once.
 */
// Fresh context and database for this class: queues are shared state, and tests must not depend on order
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class EscalationIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SlaWatch watch;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void overdueWorkIsRemindedEscalatedAndRecorded() throws Exception {
        // A second pharmacist exists, so a held request can go back to the queue
        Api admin = Api.login(mvc, "ADMIN");
        admin.post("/api/admin/users", """
                {"username":"PHARM02","fullName":"Second Pharmacist","role":"PHARMACIST","dispensaryId":1}
                """).andExpect(status().isCreated());

        Api employee = Api.login(mvc, "EMP1002");
        String rx = employee.upload("PRESCRIPTION", "rx.pdf", "sla-rx");
        employee.post("/api/nac", """
                        {"dispensaryId":1,"prescriptionDate":"%s","prescribedBy":"Dr. Demo",
                         "prescriptionDocumentId":"%s",
                         "items":[{"itemName":"Tab. Losartan 50","itemType":"MEDICINE","quantity":"30 tablets"}]}
                        """.formatted(LocalDate.now().minusDays(1), rx))
                .andExpect(status().isCreated());
        Api pharmacist = Api.login(mvc, "PHARM01");
        MvcResult taken = pharmacist.post("/api/nac/queue/take-next", null).andExpect(status().isOk()).andReturn();
        String id = Api.read(taken, "$.id");
        String ref = "e-NAC request " + id;

        // The request entered the pharmacist stage 41 hours ago; the watch then runs at later times
        backdate(id, Duration.ofHours(41));
        Instant now = Instant.now();

        // 1. At 85% of the 2 day limit: the holder is reminded
        watch.runAt(now);
        pharmacist.get("/api/notifications")
                .andExpect(jsonPath("$[?(@.title == 'Time limit approaching: " + ref + "')]").exists());

        // 2. Past the limit: pharmacists and the Medical Officer are told, the employee is informed,
        //    and the request goes back to the queue because a colleague can take it
        watch.runAt(now.plus(Duration.ofHours(9)));
        Api mo = Api.login(mvc, "MO01");
        mo.get("/api/notifications").andExpect(jsonPath("$[?(@.title == 'Time limit passed: " + ref + "')]").exists());
        employee.get("/api/notifications")
                .andExpect(jsonPath("$[?(@.title == '" + ref + " is taking longer than it should')]").exists());
        assertThat(jdbc.queryForObject("select assigned_to from nac_request where id = ?", Long.class,
                Long.valueOf(id))).isNull();

        // 3. Past twice the limit: the zonal oversight officer of the employee's school is told
        watch.runAt(now.plus(Duration.ofHours(60)));
        watch.runAt(now.plus(Duration.ofHours(61)));
        Api dde = Api.login(mvc, "DDE06");
        dde.get("/api/notifications")
                .andExpect(jsonPath("$[?(@.title == 'Escalated: " + ref + "')]").exists());
        // Every step happens once, however often the watch runs
        assertThat(jdbc.queryForObject("select count(*) from notification n join user_account u "
                + "on u.id = n.recipient_user_id where u.username = 'DDE06' and n.title = ?", Integer.class,
                "Escalated: " + ref)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sla_event where subject_type = 'NAC' and subject_id = ?",
                Integer.class, Long.valueOf(id))).isEqualTo(3);

        // 4. The oversight screen shows it, and a reminder can be sent once an hour
        backdate(id, Duration.ofHours(100));
        dde.get("/api/oversight")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zone").value("Zone 6"))
                .andExpect(jsonPath("$.items[?(@.reference == '" + ref + "')].level").value("ESCALATED"));
        dde.post("/api/oversight/remind", "{\"subjectType\":\"NAC\",\"subjectId\":" + id + "}")
                .andExpect(status().isNoContent());
        dde.post("/api/oversight/remind", "{\"subjectType\":\"NAC\",\"subjectId\":" + id + "}")
                .andExpect(jsonPath("$.code").value("RECENTLY_REMINDED"));
        pharmacist.get("/api/notifications")
                .andExpect(jsonPath("$[?(@.title == 'Reminder from the zonal office: " + ref + "')]").exists());

        // Oversight is limited to oversight officers and administrators
        employee.get("/api/oversight").andExpect(status().isForbidden());

        // 5. The steps were also queued as e-mail for officials with an address on record
        assertThat(jdbc.queryForObject("select count(*) from outbound_message m join user_account u "
                + "on u.id = m.recipient_user_id where u.username = 'PHARM01' and m.channel = 'EMAIL'",
                Integer.class)).isGreaterThan(0);
    }

    private void backdate(String nacId, Duration age) {
        jdbc.update("update nac_request set stage_entered_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(age)), Long.valueOf(nacId));
    }
}
