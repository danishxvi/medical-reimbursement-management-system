package com.mrms.enac.internal;

import com.mrms.document.DocumentMeta;
import com.mrms.enac.NacTypes.Decision;
import com.mrms.enac.NacTypes.ItemType;
import com.mrms.enac.NacTypes.NacStatus;
import com.mrms.shared.domain.Relation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the e-NAC endpoints. */
final class NacDtos {

    private NacDtos() {
    }

    // ---------------- Requests ----------------

    record ItemInput(
            @NotBlank @Size(max = 200) String itemName,
            @NotNull ItemType itemType,
            @NotBlank @Size(max = 40) String quantity) {
    }

    record CreateNacRequest(
            @NotNull Long dispensaryId,
            Long dependentId,
            @NotNull @PastOrPresent LocalDate prescriptionDate,
            @NotBlank @Size(max = 120) String prescribedBy,
            @NotNull UUID prescriptionDocumentId,
            @NotEmpty @Size(max = 40) List<@Valid ItemInput> items) {
    }

    record ResubmitNacRequest(
            @NotNull @PastOrPresent LocalDate prescriptionDate,
            @NotBlank @Size(max = 120) String prescribedBy,
            @NotNull UUID prescriptionDocumentId,
            @NotEmpty @Size(max = 40) List<@Valid ItemInput> items) {
    }

    record ItemDecisionInput(
            @NotNull Long itemId,
            @NotNull Decision decision,
            @Size(max = 300) String reason) {
    }

    record PharmacistReviewRequest(
            @NotEmpty List<@Valid ItemDecisionInput> decisions,
            @Size(max = 500) String remarks) {
    }

    record RemarksRequest(@NotBlank @Size(max = 500) String remarks) {
    }

    record CountersignRequest(
            @NotBlank @Size(max = 128) String password,
            @Size(max = 500) String remarks) {
    }

    // ---------------- Responses ----------------

    record ItemView(Long id, int lineNo, String itemName, ItemType itemType, String quantity, Decision decision,
                    String decisionReason, String decidedBy, Instant decidedAt) {
    }

    record NacView(
            Long id,
            String nacNumber,
            NacStatus status,
            Long employeeUserId,
            String employeeName,
            Long dispensaryId,
            String dispensaryName,
            String patientName,
            Relation patientRelation,
            LocalDate prescriptionDate,
            String prescribedBy,
            DocumentMeta prescription,
            List<ItemView> items,
            Instant createdAt,
            Instant queueSince,
            Instant stageEnteredAt,
            Integer queuePosition,
            boolean overdue,
            String assignedTo,
            boolean assignedToMe,
            String pharmacistName,
            Instant pharmacistAt,
            String pharmacistRemarks,
            String medicalOfficerName,
            Instant medicalOfficerAt,
            String medicalOfficerRemarks,
            String returnRemarks,
            Instant issuedAt) {
    }

    record NacSummary(
            Long id,
            String nacNumber,
            NacStatus status,
            String employeeName,
            String patientName,
            Relation patientRelation,
            String dispensaryName,
            LocalDate prescriptionDate,
            int itemCount,
            Instant createdAt,
            Instant queueSince,
            Instant stageEnteredAt,
            boolean overdue,
            String assignedTo) {
    }

    record QueueView(NacStatus stage, int slaDays, NacView current, List<NacSummary> waiting) {
    }
}
