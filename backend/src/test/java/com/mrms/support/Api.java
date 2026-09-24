package com.mrms.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small test client: logs in through the real login endpoint and keeps the
 * resulting session, so tests exercise the same security path as the browser.
 */
public final class Api {

    public static final String DEMO_PASSWORD = "Demo@Pass2026";

    private final MockMvc mvc;
    private final MockHttpSession session;

    private Api(MockMvc mvc, MockHttpSession session) {
        this.mvc = mvc;
        this.session = session;
    }

    public static Api login(MockMvc mvc, String username) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return new Api(mvc, (MockHttpSession) result.getRequest().getSession(false));
    }

    public ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).session(session));
    }

    public ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    public ResultActions put(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(url).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions delete(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(url).session(session).with(csrf()));
    }

    /** Uploads a small valid PDF (unique per marker) and returns its document id. */
    public String upload(String category, String name, String marker) throws Exception {
        byte[] pdf = ("%PDF-1.4\n% " + marker + "\n1 0 obj << /Type /Catalog >> endobj\n%%EOF")
                .getBytes(StandardCharsets.ISO_8859_1);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(new MockMultipartFile("file", name, "application/pdf", pdf))
                        .param("category", category)
                        .session(session).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.id");
    }

    public MockHttpSession session() {
        return session;
    }

    public static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }
}
