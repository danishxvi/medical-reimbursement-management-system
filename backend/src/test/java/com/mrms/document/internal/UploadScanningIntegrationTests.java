package com.mrms.document.internal;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload path with a scanner that flags a marker string, standing in for
 * ClamAV: infected files are refused, clean ones get a standard name.
 */
// Fresh context and database for this class: queues are shared state, and tests must not depend on order
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class UploadScanningIntegrationTests {

    private static final String MARKER = "FAKE-MALWARE-MARKER";

    @TestConfiguration
    static class MarkerScannerConfig {

        @Bean
        @Primary
        VirusScanner markerScanner() {
            return content -> new String(content, StandardCharsets.ISO_8859_1).contains(MARKER)
                    ? new VirusScanner.Result(VirusScanner.Status.INFECTED, "Test.Marker", "test", Instant.now())
                    : new VirusScanner.Result(VirusScanner.Status.CLEAN, null, "test", Instant.now());
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void infectedUploadIsRefusedAndCleanUploadIsRenamed() throws Exception {
        Api employee = Api.login(mvc, "EMP1002");

        mvc.perform(multipart("/api/documents")
                        .file(pdf("my scan (final).pdf", MARKER))
                        .param("category", "BILL")
                        .with(employee.auth()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MALWARE_DETECTED"));

        mvc.perform(multipart("/api/documents")
                        .file(pdf("my scan (final).pdf", "clean content"))
                        .param("category", "BILL")
                        .with(employee.auth()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.standardName").value(matchesPattern("EMP1002_BILL_\\d{8}_\\d{2}\\.pdf")))
                .andExpect(jsonPath("$.originalName").value("my scan (final).pdf"))
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"));
    }

    private static MockMultipartFile pdf(String name, String body) {
        byte[] bytes = ("%PDF-1.4\n% " + body + "\n1 0 obj << /Type /Catalog >> endobj\n%%EOF")
                .getBytes(StandardCharsets.ISO_8859_1);
        return new MockMultipartFile("file", name, "application/pdf", bytes);
    }
}
