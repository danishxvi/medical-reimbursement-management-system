package com.mrms;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Privacy notice acknowledgement and the first login guide. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class OnboardingIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void privacyNoticeIsPublic() throws Exception {
        mvc.perform(get("/api/legal/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").exists())
                .andExpect(jsonPath("$.sections[0].heading").exists());
    }

    @Test
    void noticeAndGuideAreRecordedPerUser() throws Exception {
        Api hos = Api.login(mvc, "HOS9900002");
        hos.get("/api/auth/me")
                .andExpect(jsonPath("$.privacyAccepted").value(false))
                .andExpect(jsonPath("$.guideSeen").value(false));

        // An outdated version cannot be acknowledged
        hos.post("/api/auth/privacy/accept", "{\"version\":\"0.1\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOTICE_CHANGED"));

        String version = Api.read(mvc.perform(get("/api/legal/privacy")).andReturn(), "$.version");
        hos.post("/api/auth/privacy/accept", "{\"version\":\"" + version + "\"}").andExpect(status().isNoContent());
        hos.post("/api/auth/guide/seen", null).andExpect(status().isNoContent());

        hos.get("/api/auth/me")
                .andExpect(jsonPath("$.privacyAccepted").value(true))
                .andExpect(jsonPath("$.guideSeen").value(true));
    }
}
