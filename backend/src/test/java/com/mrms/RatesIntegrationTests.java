package com.mrms;

import com.mrms.rates.RateBasis;
import com.mrms.rates.RateLookup;
import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The seeded CGHS 2025 list, search, and an administrator importing a revision. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class RatesIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RateLookup rates;

    @Test
    void seededListGivesTheOfficialRates() {
        LocalDate billDate = LocalDate.of(2026, 1, 15);
        // Serial 15, LB012 Complete Haemogram: Non-NABH 255, NABH 300 (Annexure I, Tier I)
        RateLookup.RateQuote cbc = rates.quote("lb012", billDate, RateBasis.NABH, "GENERAL", true).orElseThrow();
        assertThat(cbc.applicableRate()).isEqualByComparingTo("300.00");
        assertThat(cbc.reference()).isEqualTo("CGHS-2025-T1/LB012/NABH");
        assertThat(rates.quote("LB012", billDate, RateBasis.NON_NABH, null, false).orElseThrow().applicableRate())
                .isEqualByComparingTo("255.00");

        // Before the list took effect (13.10.2025) there is no rate, rather than a wrong one
        assertThat(rates.quote("LB012", LocalDate.of(2025, 10, 12), RateBasis.NABH, null, false)).isEmpty();
        assertThat(rates.quote("LB012", billDate, RateBasis.AS_BILLED, null, false)).isEmpty();
    }

    @Test
    void employeesCanSearchCodesAndAdminsCanImportRevisions() throws Exception {
        Api employee = Api.login(mvc, "EMP1002");
        employee.get("/api/rates/search?q=haemogram&date=2026-01-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("LB012")));

        Api admin = Api.login(mvc, "ADMIN");
        employee.get("/api/admin/rates/lists").andExpect(status().isForbidden());

        String csv = """
                code,name,speciality,non_nabh,nabh,super_speciality
                LB012,Complete Haemogram,Laboratory Investigation,280,330,330
                """;
        MvcResult imported = mvc.perform(multipart("/api/admin/rates/lists")
                        .file(new MockMultipartFile("file", "rates.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("code", "TEST-2027")
                        .param("title", "Test revision")
                        .param("orderReference", "Test order")
                        .param("cityTier", "X")
                        .param("effectiveFrom", "2027-04-01")
                        .with(admin.auth()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andReturn();
        String id = Api.read(imported, "$.id");

        // Imported lists change nothing until activated
        assertThat(rates.quote("LB012", LocalDate.of(2027, 5, 1), RateBasis.NABH, null, false)
                .orElseThrow().applicableRate()).isEqualByComparingTo("300.00");

        admin.post("/api/admin/rates/lists/" + id + "/activate", null).andExpect(status().isOk());
        assertThat(rates.quote("LB012", LocalDate.of(2027, 5, 1), RateBasis.NABH, null, false)
                .orElseThrow().applicableRate()).isEqualByComparingTo("330.00");
        // The older list now ends the day before, so earlier bills keep their rate
        assertThat(rates.quote("LB012", LocalDate.of(2027, 3, 31), RateBasis.NABH, null, false)
                .orElseThrow().applicableRate()).isEqualByComparingTo("300.00");

        admin.post("/api/admin/rates/lists/" + id + "/deactivate", null).andExpect(status().isOk());
    }
}
