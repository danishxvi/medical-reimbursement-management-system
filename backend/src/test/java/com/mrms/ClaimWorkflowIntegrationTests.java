package com.mrms;

import com.mrms.support.Api;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks one claim through the complete digital process with the demo
 * accounts: e-NAC at the dispensary, claim by the employee, verification by
 * the Head of School, scrutiny and sanction at the PAO, budget and payment.
 * Along the way it checks the rules that address the problem statement.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ClaimWorkflowIntegrationTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void claimTravelsFromDispensaryToPayment() throws Exception {
        LocalDate today = LocalDate.now();
        Api employee = Api.login(mvc, "EMP1001");

        // ---------------- 1. e-NAC at the dispensary ----------------
        String rx = employee.upload("PRESCRIPTION", "prescription.pdf", "rx-1");
        MvcResult nac = employee.post("/api/nac", """
                        {"dispensaryId":1,"prescriptionDate":"%s","prescribedBy":"Dr. Demo",
                         "prescriptionDocumentId":"%s",
                         "items":[{"itemName":"Tab. Metformin 500","itemType":"MEDICINE","quantity":"30 tablets"},
                                  {"itemName":"Tab. Paracetamol 650","itemType":"MEDICINE","quantity":"10 tablets"}]}
                        """.formatted(today.minusDays(5), rx))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PHARMACIST"))
                .andReturn();
        String nacId = Api.read(nac, "$.id");

        Api pharmacist = Api.login(mvc, "PHARM01");
        MvcResult taken = pharmacist.post("/api/nac/queue/take-next", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMe").value(true))
                .andReturn();
        String item1 = Api.read(taken, "$.items[0].id");
        String item2 = Api.read(taken, "$.items[1].id");

        // Not admissible needs a reason: accountability for every decision
        pharmacist.post("/api/nac/" + nacId + "/pharmacist-review", """
                        {"decisions":[{"itemId":%s,"decision":"NOT_AVAILABLE"},
                                      {"itemId":%s,"decision":"NOT_ADMISSIBLE"}]}
                        """.formatted(item1, item2))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        pharmacist.post("/api/nac/" + nacId + "/pharmacist-review", """
                        {"decisions":[{"itemId":%s,"decision":"NOT_AVAILABLE"},
                                      {"itemId":%s,"decision":"AVAILABLE"}]}
                        """.formatted(item1, item2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_MEDICAL_OFFICER"))
                .andExpect(jsonPath("$.items[0].decidedBy").value("Vikas Gupta"));

        Api mo = Api.login(mvc, "MO01");
        mo.post("/api/nac/queue/take-next", null).andExpect(status().isOk());
        // Countersigning requires the MO's password
        mo.post("/api/nac/" + nacId + "/countersign", "{\"password\":\"wrong-password\"}")
                .andExpect(jsonPath("$.code").value("PASSWORD_CONFIRMATION_FAILED"));
        mo.post("/api/nac/" + nacId + "/countersign", "{\"password\":\"" + Api.DEMO_PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.nacNumber", containsString("NAC/DSP-D01/")));

        // Only the NOT_AVAILABLE item may be claimed
        employee.get("/api/claims/claimable-nac-items")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nacItemId").value(Long.parseLong(item1)));

        // ---------------- 2. Claim by the employee ----------------
        String bill = employee.upload("BILL", "pharmacy-bill.pdf", "bill-1");
        String consultBill = employee.upload("BILL", "consultation.pdf", "bill-2");
        String card = employee.upload("DGEHS_CARD", "card.pdf", "card-1");
        String claimBody = """
                {"illnessDescription":"Type 2 diabetes follow up","treatmentType":"OPD",
                 "treatmentFrom":"%s","treatmentTo":"%s","hospitalName":"Demo DGEHS Dispensary",
                 "hospitalType":"GOVERNMENT","emergency":false,
                 "items":[{"category":"MEDICINE","description":"Tab. Metformin 500","billNumber":"B-101",
                           "billDate":"%s","vendorName":"Demo Chemist","amountClaimed":450.00,
                           "nacItemId":%s,"legacyNac":false,"billDocumentId":"%s"},
                          {"category":"CONSULTATION","description":"Specialist consultation","billNumber":"C-77",
                           "billDate":"%s","vendorName":"Demo Clinic","amountClaimed":500.00,
                           "legacyNac":false,"billDocumentId":"%s"}],
                 "attachments":[{"documentId":"%s","category":"DGEHS_CARD"}]}
                """.formatted(today.minusDays(5), today.minusDays(1), today.minusDays(4), item1, bill,
                today.minusDays(5), consultBill, card);
        MvcResult draft = employee.post("/api/claims", claimBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.claimedAmount").value(950.0))
                .andExpect(jsonPath("$.employee.dgehsCardNo").value("DG/2019/445566"))
                .andReturn();
        String claimId = Api.read(draft, "$.id");

        // Undertaking must be accepted
        employee.post("/api/claims/" + claimId + "/submit", "{\"undertakingAccepted\":false}")
                .andExpect(status().isBadRequest());
        MvcResult submitted = employee.post("/api/claims/" + claimId + "/submit", "{\"undertakingAccepted\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_HOS"))
                .andExpect(jsonPath("$.claimNumber", containsString("MR/9900001/")))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andReturn();
        String firstSubmittedAt = Api.read(submitted, "$.firstSubmittedAt");

        // The same e-NAC item or bill cannot be claimed twice
        employee.post("/api/claims", claimBody).andExpect(status().isCreated()).andDo(r -> {
            String dup = Api.read(r, "$.id");
            employee.post("/api/claims/" + dup + "/submit", "{\"undertakingAccepted\":true}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail", containsString("already part of another claim")));
            employee.delete("/api/claims/" + dup).andExpect(status().isNoContent());
        });

        // ---------------- 3. Head of School ----------------
        // HoS of another school cannot even see the claim
        Api otherHos = Api.login(mvc, "HOS9900002");
        otherHos.get("/api/claims/" + claimId).andExpect(status().isNotFound());

        Api hos = Api.login(mvc, "HOS9900001");
        MvcResult hosView = hos.post("/api/claims/queue/take-next", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(claimId)))
                .andExpect(jsonPath("$.allowedActions", hasItem("FORWARD")))
                .andReturn();
        String line1 = Api.read(hosView, "$.items[0].id");
        String line2 = Api.read(hosView, "$.items[1].id");

        // Return for a minor correction instead of rejecting
        hos.post("/api/claims/" + claimId + "/return",
                        "{\"reasons\":[\"AMOUNT_MISMATCH\"],\"remarks\":\"Consultation bill shows 450\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED_BY_HOS"));

        // Employee corrects only what was flagged and resubmits; seniority is kept
        employee.put("/api/claims/" + claimId, claimBody.replace("\"amountClaimed\":500.00", "\"amountClaimed\":450.00"))
                .andExpect(status().isOk());
        employee.post("/api/claims/" + claimId + "/submit", "{\"undertakingAccepted\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_HOS"))
                .andExpect(jsonPath("$.firstSubmittedAt").value(firstSubmittedAt))
                .andExpect(jsonPath("$.returnCount").value(1));

        hosView = hos.post("/api/claims/queue/take-next", null).andExpect(status().isOk()).andReturn();
        line1 = Api.read(hosView, "$.items[0].id");
        line2 = Api.read(hosView, "$.items[1].id");
        hos.post("/api/claims/" + claimId + "/forward", """
                        {"items":[{"itemId":%s,"dgehsRate":400,"amountRestricted":400,"remarks":"DGEHS rate"},
                                  {"itemId":%s,"amountRestricted":450}],
                         "certificateAccepted":true,"password":"%s"}
                        """.formatted(line1, line2, Api.DEMO_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAO_AUDIT"))
                .andExpect(jsonPath("$.restrictedAmount").value(850.0));

        // ---------------- 4. PAO: maker then checker ----------------
        Api auditor = Api.login(mvc, "AUD01");
        auditor.post("/api/claims/queue/take-next", null).andExpect(status().isOk());
        auditor.post("/api/claims/" + claimId + "/audit", """
                        {"items":[{"itemId":%s,"amountAdmitted":400},{"itemId":%s,"amountAdmitted":450}],
                         "recommendation":"SANCTION"}
                        """.formatted(line1, line2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SANCTION"))
                .andExpect(jsonPath("$.admittedAmount").value(850.0));

        Api officer = Api.login(mvc, "PAO01");
        officer.post("/api/claims/queue/take-next", null).andExpect(status().isOk());
        officer.post("/api/claims/" + claimId + "/sanction", "{\"password\":\"" + Api.DEMO_PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SANCTIONED"));

        // ---------------- 5. Budget and payment ----------------
        // No money yet: payment run refuses, claim waits without being sent back
        officer.post("/api/budget/pao/schools/1/pay", "{\"password\":\"" + Api.DEMO_PASSWORD + "\"}")
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        hos.get("/api/budget/school")
                .andExpect(jsonPath("$.position.awaitingFunds").value(850.0))
                .andExpect(jsonPath("$.position.suggestedDemand").value(850.0));
        hos.post("/api/budget/school/demand", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(850.0));

        officer.post("/api/budget/pao/schools/1/allocations",
                        "{\"amount\":100000,\"sanctionOrderNo\":\"SO/DEMO/1\"}")
                .andExpect(status().isCreated());
        officer.post("/api/budget/pao/schools/1/pay", "{\"password\":\"" + Api.DEMO_PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimCount").value(1))
                .andExpect(jsonPath("$.totalAmount").value(850.0));

        employee.get("/api/claims/" + claimId)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.timeline.length()", equalTo(7)));

        // The employee was told at each step
        employee.get("/api/notifications").andExpect(jsonPath("$[0].title", containsString("paid")));

        // ---------------- 6. Printable claim ----------------
        MvcResult pdf = employee.get("/api/claims/" + claimId + "/pdf")
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("application/pdf"))
                .andReturn();
        org.openpdf.text.pdf.PdfReader reader = new org.openpdf.text.pdf.PdfReader(pdf.getResponse().getContentAsByteArray());
        StringBuilder text = new StringBuilder();
        org.openpdf.text.pdf.parser.PdfTextExtractor extractor = new org.openpdf.text.pdf.parser.PdfTextExtractor(reader);
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append(System.lineSeparator());
        }
        org.assertj.core.api.Assertions.assertThat(text.toString())
                .contains("Annexure I", "Annexure II", "Calculation sheet", "Certificate by Head of School",
                        "Index of attached documents", "Record fingerprint");
        // Anyone outside the claim's offices cannot print it either
        otherHos.get("/api/claims/" + claimId + "/pdf").andExpect(status().isNotFound());

        // ---------------- 7. Audit chain is intact ----------------
        Api admin = Api.login(mvc, "ADMIN");
        admin.get("/api/admin/audit/verify").andExpect(jsonPath("$.valid").value(true));
        // Administrators get aggregates, never individual medical claims
        admin.get("/api/claims/" + claimId).andExpect(status().isForbidden());
    }
}
