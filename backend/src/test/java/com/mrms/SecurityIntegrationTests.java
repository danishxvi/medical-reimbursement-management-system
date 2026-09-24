package com.mrms;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class SecurityIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/claims/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"EMP1001\",\"password\":\"" + Api.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void failedLoginsAreGenericAndLockTheAccount() throws Exception {
        String unknown = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"NOBODY\",\"password\":\"Whatever@1234\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        for (int i = 0; i < 5; i++) {
            String wrong = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"EMP2001\",\"password\":\"Wrong@Pass1234\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            // Same message whether or not the user exists: no username enumeration
            org.assertj.core.api.Assertions.assertThat(wrong).isEqualTo(unknown);
        }
        // Correct password is refused while the account is locked
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"EMP2001\",\"password\":\"" + Api.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesCannotCrossBoundaries() throws Exception {
        Api employee = Api.login(mvc, "EMP1002");
        employee.get("/api/admin/users").andExpect(status().isForbidden());
        employee.get("/api/claims/queue").andExpect(status().isForbidden());
        employee.post("/api/nac/queue/take-next", null).andExpect(status().isForbidden());

        Api hos = Api.login(mvc, "HOS9900002");
        hos.post("/api/claims", "{}").andExpect(status().isForbidden());
        hos.get("/api/budget/pao/schools").andExpect(status().isForbidden());
    }

    @Test
    void newLoginEndsTheOlderSession() throws Exception {
        Api first = Api.login(mvc, "AUD02");
        first.get("/api/auth/me").andExpect(status().isOk());
        Api.login(mvc, "AUD02");
        first.get("/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
    }

    @Test
    void uploadsAreValidatedByContent() throws Exception {
        Api employee = Api.login(mvc, "EMP1002");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "bill.pdf",
                                "application/pdf", "MZ this is not a pdf".getBytes()))
                        .param("category", "BILL")
                        .session(employee.session()).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"));
    }
}
