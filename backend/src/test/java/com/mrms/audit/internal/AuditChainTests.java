package com.mrms.audit.internal;

import com.mrms.audit.AuditTrail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the audit trail is tamper evident: editing any stored row breaks
 * verification at exactly that row.
 */
// Fresh context and database for this class: queues are shared state, and tests must not depend on order
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class AuditChainTests {

    @Autowired
    private AuditTrail audit;

    @Autowired
    private AuditService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void editingARowIsDetected() {
        audit.recordAnonymous("TESTER", "TEST_EVENT", "first");
        audit.recordAnonymous("TESTER", "TEST_EVENT", "second");
        assertThat(service.verify().valid()).isTrue();

        Long target = jdbc.queryForObject(
                "select max(id) from audit_entry where details = 'first'", Long.class);
        String original = jdbc.queryForObject("select details from audit_entry where id = ?", String.class, target);
        try {
            // Simulates someone quietly editing the database
            jdbc.update("update audit_entry set details = 'edited' where id = ?", target);
            AuditService.ChainVerification result = service.verify();
            assertThat(result.valid()).isFalse();
            assertThat(result.firstBrokenEntryId()).isEqualTo(target);
        } finally {
            jdbc.update("update audit_entry set details = ? where id = ?", original, target);
        }
        assertThat(service.verify().valid()).isTrue();
    }
}
