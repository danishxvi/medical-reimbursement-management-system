package com.mrms.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small test client: logs in through the real login endpoint and sends the
 * session cookie it receives, exactly like a browser. Sessions live in the
 * database (Spring Session), so the cookie is the only link to them.
 */
public final class Api {

    public static final String DEMO_PASSWORD = "Demo@Pass2026";
    public static final String SESSION_COOKIE = "MRMS_SESSION";

    private final MockMvc mvc;
    private final Cookie session;

    private Api(MockMvc mvc, Cookie session) {
        this.mvc = mvc;
        this.session = session;
    }

    public static Api login(MockMvc mvc, String username) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return new Api(mvc, sessionCookie(result));
    }

    /** The session cookie set by a response (Spring Session writes it as a Set-Cookie header). */
    static Cookie sessionCookie(MvcResult result) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(SESSION_COOKIE + "="))
                .map(h -> new Cookie(SESSION_COOKIE, h.substring(SESSION_COOKIE.length() + 1, h.indexOf(';'))))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("No session cookie in the login response"));
    }

    public ResultActions get(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).cookie(session));
    }

    public ResultActions post(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json == null ? "{}" : json));
    }

    public ResultActions put(String url, String json) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(url).cookie(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions delete(String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(url).cookie(session).with(csrf()));
    }

    /** Uploads a small valid PDF (unique per marker) and returns its document id. */
    public String upload(String category, String name, String marker) throws Exception {
        byte[] pdf = ("%PDF-1.4\n% " + marker + "\n1 0 obj << /Type /Catalog >> endobj\n%%EOF")
                .getBytes(StandardCharsets.ISO_8859_1);
        MvcResult result = mvc.perform(MockMvcRequestBuilders.multipart("/api/documents")
                        .file(new MockMultipartFile("file", name, "application/pdf", pdf))
                        .param("category", category)
                        .cookie(session).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result, "$.id");
    }

    /** Attaches this user's session to any request built by a test. */
    public RequestPostProcessor auth() {
        return request -> {
            request.setCookies(session);
            return request;
        };
    }

    public static String read(MvcResult result, String path) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value == null ? null : value.toString();
    }
}
