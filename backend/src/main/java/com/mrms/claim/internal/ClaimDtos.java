package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import com.mrms.claim.internal.ClaimEnums.HospitalType;
import com.mrms.claim.internal.ClaimEnums.ItemCategory;
import com.mrms.claim.internal.ClaimEnums.Recommendation;
import com.mrms.claim.internal.ClaimEnums.ReturnReason;
import com.mrms.claim.internal.ClaimEnums.TreatmentType;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.enac.NacTypes.Decision;
import com.mrms.organisation.OrganisationViews.EmployeeProfileView;
import com.mrms.shared.domain.Relation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the claim endpoints. */
final class ClaimDtos {

    private ClaimDtos() {
    }

    // ==================================================================
    // Employee input
    // ==================================================================

    record ItemInput(
            @NotNull ItemCategory category,
            @NotBlank @Size(max = 200) String description,
            @NotBlank @Size(max = 50) String billNumber,
            @NotNull LocalDate billDate,
            @NotBlank @Size(max = 150) String vendorName,
            @Size(max = 30) String dgehsCode,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("9999999.99") @Digits(integer = 7, fraction = 2)
            BigDecimal amountClaimed,
            Long nacItemId,
            boolean legacyNac,
            @NotNull UUID billDocumentId) {
    }

    record AttachmentInput(@NotNull UUID documentId, @NotNull DocumentCategory category) {
    }

    record ClaimInput(
            Long dependentId,
            @NotBlank @Size(max = 500) String illnessDescription,
            @NotNull TreatmentType treatmentType,
            @NotNull LocalDate treatmentFrom,
            @NotNull LocalDate treatmentTo,
            LocalDate admissionDate,
            LocalDate dischargeDate,
            @NotBlank @Size(max = 200) String hospitalName,
            @Size(max = 300) String hospitalAddress,
            @NotNull HospitalType hospitalType,
            boolean emergency,
            @Size(max = 300) String referralDetails,
            @Size(max = 300) String medicalAdvanceDetails,
            @Size(max = 50) List<@Valid ItemInput> items,
            @Size(max = 30) List<@Valid AttachmentInput> attachments) {
    }

    record SubmitRequest(
            @AssertTrue(message = "Accept the undertaking to submit") boolean undertakingAccepted) {
    }

    // ==================================================================
    // Reviewer input
    // ==================================================================

    record Restriction(
            @NotNull Long itemId,
            @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal dgehsRate,
            @NotNull @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal amountRestricted,
            @Size(max = 300) String remarks) {
    }

    record HosForwardRequest(
            @NotEmpty List<@Valid Restriction> items,
            @AssertTrue(message = "Confirm the certificate to forward the claim") boolean certificateAccepted,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 1000) String remarks) {
    }

    record ReturnRequest(
            @NotEmpty @Size(max = 12) List<ReturnReason> reasons,
            @NotBlank @Size(max = 1000) String remarks) {
    }

    record Admission(
            @NotNull Long itemId,
            @NotNull @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal amountAdmitted,
            @Size(max = 300) String disallowReason) {
    }

    record AuditRequest(
            @NotEmpty List<@Valid Admission> items,
            @NotNull Recommendation recommendation,
            @Size(max = 1000) String remarks) {
    }

    record SanctionRequest(
            @NotBlank @Size(max = 128) String password,
            @Size(max = 1000) String remarks) {
    }

    record RejectRequest(
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 1000) String reason) {
    }

    record RemarksRequest(@NotBlank @Size(max = 1000) String remarks) {
    }

    // ==================================================================
    // Responses
    // ==================================================================

    record NacLink(Long nacRequestId, String nacNumber, String itemName, Decision decision, String dispensaryName,
                   UUID prescriptionDocumentId, LocalDate prescriptionDate) {
    }

    record ItemView(
            Long id,
            int lineNo,
            ItemCategory category,
            String description,
            String billNumber,
            LocalDate billDate,
            String vendorName,
            String dgehsCode,
            BigDecimal amountClaimed,
            BigDecimal dgehsRate,
            BigDecimal amountRestricted,
            String hosRemarks,
            BigDecimal amountAdmitted,
            String disallowReason,
            Long nacItemId,
            boolean legacyNac,
            NacLink nac,
            DocumentMeta billDocument) {
    }

    record AttachmentView(DocumentCategory category, DocumentMeta document) {
    }

    record ChecklistEntry(String code, String label, boolean submitted, boolean required) {
    }

    record TimelineEntry(String action, ClaimStatus fromStatus, ClaimStatus toStatus, String actorName,
                         String actorRole, String remarks, List<String> reasons, Instant occurredAt) {
    }

    record ClaimView(
            Long id,
            String claimNumber,
            ClaimStatus status,
            String statusLabel,
            EmployeeProfileView employee,
            String patientName,
            Relation patientRelation,
            Long dependentId,
            String illnessDescription,
            TreatmentType treatmentType,
            LocalDate treatmentFrom,
            LocalDate treatmentTo,
            LocalDate admissionDate,
            LocalDate dischargeDate,
            String hospitalName,
            String hospitalAddress,
            HospitalType hospitalType,
            boolean emergency,
            String referralDetails,
            String medicalAdvanceDetails,
            BigDecimal claimedAmount,
            BigDecimal restrictedAmount,
            BigDecimal admittedAmount,
            Map<String, Map<String, BigDecimal>> totalsByCategory,
            List<ItemView> items,
            List<AttachmentView> attachments,
            List<ChecklistEntry> checklist,
            List<TimelineEntry> timeline,
            String financialYear,
            Instant createdAt,
            Instant firstSubmittedAt,
            Instant lastSubmittedAt,
            Instant stageEnteredAt,
            Integer queuePosition,
            boolean overdue,
            int slaDays,
            String assignedTo,
            boolean assignedToMe,
            int returnCount,
            String hosCertifiedBy,
            Instant hosCertifiedAt,
            String auditedBy,
            Instant auditedAt,
            Recommendation auditRecommendation,
            String sanctionedBy,
            Instant sanctionedAt,
            Instant paidAt,
            String paymentBatchRef,
            String rejectionReason,
            Map<UUID, String> documentNames,
            List<String> allowedActions) {
    }

    record ClaimSummary(
            Long id,
            String claimNumber,
            ClaimStatus status,
            String statusLabel,
            String employeeName,
            String patientName,
            Relation patientRelation,
            TreatmentType treatmentType,
            BigDecimal claimedAmount,
            BigDecimal restrictedAmount,
            BigDecimal admittedAmount,
            Instant createdAt,
            Instant firstSubmittedAt,
            Instant stageEnteredAt,
            boolean overdue,
            String assignedTo,
            int returnCount) {
    }

    record QueueView(ClaimStatus stage, String stageLabel, int slaDays, ClaimView current,
                     List<ClaimSummary> waiting) {
    }

    record ClaimableNacItem(Long nacItemId, Long nacRequestId, String nacNumber, String itemName, String quantity,
                            String patientName, Relation patientRelation, Long dependentId,
                            LocalDate prescriptionDate, Instant issuedAt) {
    }

    record Option(String value, String label) {
    }

    record ClaimMeta(
            List<Option> treatmentTypes,
            List<Option> hospitalTypes,
            List<Option> itemCategories,
            List<Option> returnReasons,
            List<Option> documentCategories,
            List<String> undertaking,
            List<String> hosCertificate,
            int submissionWindowDays,
            int maxItems) {
    }
}
