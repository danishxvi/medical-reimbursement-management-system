package com.mrms.esign.internal;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aadhaar eSign end to end against the built in ESP simulator: the Medical
 * Officer countersigns an e-NAC. Also checks that a signature cannot be
 * reused, cannot be applied to changed content, and that a forged response
 * is refused.
 */
// Fresh context and database for this class: queues are shared state, and tests must not depend on order
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {"mrms.esign.mode=esign", "mrms.esign.simulator=true",
        "mrms.esign.esp-url=http://localhost/api/dev/esp/sign", "mrms.esign.public-base-url=http://localhost",
        "mrms.esign.asp-id=MRMS-TEST"})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class EsignIntegrationTests {

    private static final Pattern HIDDEN = Pattern.compile("name='?(request|eSignResponse)'? value='([^']*)'");

    @Autowired
    private MockMvc mvc;

    @Test
    void medicalOfficerCountersignsWithAadhaarEsign() throws Exception {
        String nacId = nacAwaitingCountersignature();
        Api mo = Api.login(mvc, "MO01");
        mo.get("/api/esign/config").andExpect(jsonPath("$.mode").value("ESIGN"));
        mo.post("/api/nac/queue/take-next", null).andExpect(status().isOk());

        // Password confirmation is not accepted in eSign mode
        mo.post("/api/nac/" + nacId + "/countersign", "{\"password\":\"" + Api.DEMO_PASSWORD + "\"}")
                .andExpect(jsonPath("$.code").value("ESIGN_REQUIRED"));

        // 1. Start: the portal signs a request carrying the digest of what is being countersigned
        MvcResult started = mo.post("/api/esign/start",
                        "{\"purpose\":\"NAC_COUNTERSIGN\",\"subjectId\":\"" + nacId + "\",\"payload\":{\"remarks\":\"Checked\"}}")
                .andExpect(status().isOk())
                .andReturn();
        String txn = Api.read(started, "$.txn");
        String requestXml = Api.read(started, "$.fields.eSignRequest");
        assertThat(requestXml).contains("<Esign", "aspId=\"MRMS-TEST\"", "InputHash");

        // 2. At the provider: wrong OTP first, then the right one. The Aadhaar number never reaches the portal.
        String signPage = mvc.perform(post("/api/dev/esp/sign").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("eSignRequest", requestXml))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String requestB64 = hidden(signPage, "request");
        String aadhaar = withVerhoeffDigit("99990000123");
        String wrongOtp = authorise(requestB64, aadhaar, "000000");
        assertThat(wrongOtp).contains("The OTP is not correct");
        String responseXml = hidden(authorise(requestB64, aadhaar, EspSimulatorController.OTP), "eSignResponse");

        // A forged response (signed by someone else) is refused
        EsignKeys attacker = EsignKeys.simulated();
        String forged = EsignProtocol.signedResponse(txn, true, null, null, attacker.simulator.caCertificate(),
                new byte[]{1}, java.time.Instant.now(), attacker.simulator.espKey(), attacker.simulator.espCertificate());
        String forgedLocation = callback(forged);
        assertThat(forgedLocation).contains("status=failed");

        // 3. Back at the portal: the genuine response is verified and the transaction is signed
        assertThat(callback(responseXml)).contains("status=ok").contains(txn);
        mo.get("/api/esign/" + txn)
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signerName").value("Dr Test Officer"));

        // 4. The signature covers the remarks too: changing them afterwards is refused, and nothing is consumed
        mo.post("/api/nac/" + nacId + "/countersign", "{\"esignTxn\":\"" + txn + "\",\"remarks\":\"Changed\"}")
                .andExpect(jsonPath("$.code").value("ESIGN_MISMATCH"));
        mo.post("/api/nac/" + nacId + "/countersign", "{\"esignTxn\":\"" + txn + "\",\"remarks\":\"Checked\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));

        // 5. One signature confirms one action
        mo.get("/api/esign/" + txn).andExpect(jsonPath("$.status").value("CONSUMED"));
    }

    // ------------------------------------------------------------------

    private String nacAwaitingCountersignature() throws Exception {
        Api employee = Api.login(mvc, "EMP1002");
        String rx = employee.upload("PRESCRIPTION", "rx.pdf", "esign-rx");
        MvcResult nac = employee.post("/api/nac", """
                        {"dispensaryId":1,"prescriptionDate":"%s","prescribedBy":"Dr. Demo",
                         "prescriptionDocumentId":"%s",
                         "items":[{"itemName":"Tab. Amlodipine 5","itemType":"MEDICINE","quantity":"30 tablets"}]}
                        """.formatted(LocalDate.now().minusDays(2), rx))
                .andExpect(status().isCreated()).andReturn();
        Api pharmacist = Api.login(mvc, "PHARM01");
        // Queues are first come, first served: the pharmacist gets the oldest waiting request,
        // which may be one left by another test, so work with whatever is handed out
        MvcResult taken = pharmacist.post("/api/nac/queue/take-next", null).andExpect(status().isOk()).andReturn();
        String nacId = Api.read(taken, "$.id");
        StringBuilder decisions = new StringBuilder();
        int items = Integer.parseInt(Api.read(taken, "$.items.length()"));
        for (int i = 0; i < items; i++) {
            decisions.append(i == 0 ? "" : ",").append("{\"itemId\":").append(Api.read(taken, "$.items[" + i + "].id"))
                    .append(",\"decision\":\"NOT_AVAILABLE\"}");
        }
        pharmacist.post("/api/nac/" + nacId + "/pharmacist-review", "{\"decisions\":[" + decisions + "]}")
                .andExpect(status().isOk());
        assertThat(Api.read(nac, "$.id")).isNotNull();
        return nacId;
    }

    private String authorise(String requestB64, String aadhaar, String otp) throws Exception {
        return mvc.perform(post("/api/dev/esp/authorise").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("request", requestB64).param("aadhaar", aadhaar).param("name", "Dr Test Officer")
                        .param("otp", otp).param("decision", "sign"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Posts like the browser does: cross site (foreign Origin), without the session cookie or a CSRF token. */
    private String callback(String responseXml) throws Exception {
        return mvc.perform(post("/api/esign/callback").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Origin", "https://esp.example.org")
                        .param("eSignResponse", responseXml))
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
    }

    private static String hidden(String html, String name) {
        Matcher m = HIDDEN.matcher(html);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return m.group(2).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                        .replace("&#39;", "'").replace("&amp;", "&");
            }
        }
        throw new AssertionError("No hidden field " + name + " in page");
    }

    private static String withVerhoeffDigit(String first11) {
        for (int d = 0; d <= 9; d++) {
            if (EspSimulatorController.verhoeff(first11 + d)) {
                return first11 + d;
            }
        }
        throw new AssertionError("No check digit");
    }
}
