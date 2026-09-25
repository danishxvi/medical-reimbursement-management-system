package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import com.mrms.claim.internal.ClaimEnums.HospitalType;
import com.mrms.claim.internal.ClaimEnums.Recommendation;
import com.mrms.claim.internal.ClaimEnums.TreatmentType;
import com.mrms.rates.RateBasis;
import com.mrms.shared.domain.Relation;
import com.mrms.shared.web.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregate root of a reimbursement claim.
 *
 * <p>Every status change is a method on this class that first checks the
 * current status and the acting user, so the workflow rules live in one
 * place and cannot be bypassed by a controller.
 */
@Entity
@Table(name = "claim")
class Claim {

    private static final Set<ClaimStatus> EDITABLE =
            EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.RETURNED_BY_HOS, ClaimStatus.RETURNED_BY_PAO);

    private static final Set<ClaimStatus> QUEUED =
            EnumSet.of(ClaimStatus.PENDING_HOS, ClaimStatus.PENDING_PAO_AUDIT, ClaimStatus.PENDING_SANCTION);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", unique = true)
    private String claimNumber;

    @Column(name = "employee_user_id", nullable = false)
    private Long employeeUserId;

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "pao_id", nullable = false)
    private Long paoId;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_relation", nullable = false)
    private Relation patientRelation;

    @Column(name = "dependent_id")
    private Long dependentId;

    @Column(name = "illness_description", nullable = false)
    private String illnessDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_type", nullable = false)
    private TreatmentType treatmentType;

    @Column(name = "treatment_from", nullable = false)
    private LocalDate treatmentFrom;

    @Column(name = "treatment_to", nullable = false)
    private LocalDate treatmentTo;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "hospital_name", nullable = false)
    private String hospitalName;

    @Column(name = "hospital_address")
    private String hospitalAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "hospital_type", nullable = false)
    private HospitalType hospitalType;

    @Column(nullable = false)
    private boolean emergency;

    @Column(name = "referral_details")
    private String referralDetails;

    @Column(name = "medical_advance_details")
    private String medicalAdvanceDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount = BigDecimal.ZERO;

    @Column(name = "restricted_amount", precision = 12, scale = 2)
    private BigDecimal restrictedAmount;

    @Column(name = "admitted_amount", precision = 12, scale = 2)
    private BigDecimal admittedAmount;

    @Column(name = "undertaking_accepted", nullable = false)
    private boolean undertakingAccepted;

    @Column(name = "undertaking_accepted_at")
    private Instant undertakingAcceptedAt;

    @Column(name = "financial_year")
    private String financialYear;

    /** Seniority key for every queue. Set once, never changed. */
    @Column(name = "first_submitted_at")
    private Instant firstSubmittedAt;

    @Column(name = "last_submitted_at")
    private Instant lastSubmittedAt;

    /** When the claim entered its current stage; drives SLA timers. */
    @Column(name = "stage_entered_at")
    private Instant stageEnteredAt;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "return_count", nullable = false)
    private int returnCount;

    @Column(name = "hos_certified_by")
    private Long hosCertifiedBy;

    @Column(name = "hos_certified_at")
    private Instant hosCertifiedAt;

    /** Rate column chosen by the Head of School for this claim's hospital. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_basis")
    private RateBasis rateBasis;

    @Column(name = "audited_by")
    private Long auditedBy;

    @Column(name = "audited_at")
    private Instant auditedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_recommendation")
    private Recommendation auditRecommendation;

    @Column(name = "sanctioned_by")
    private Long sanctionedBy;

    @Column(name = "sanctioned_at")
    private Instant sanctionedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_batch_ref")
    private String paymentBatchRef;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "claim_id", nullable = false)
    @OrderBy("lineNo")
    private List<ClaimItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "claim_id", nullable = false)
    @OrderBy("id")
    private List<ClaimAttachment> attachments = new ArrayList<>();

    protected Claim() {
    }

    Claim(Long employeeUserId, Long schoolId, Long paoId, Instant now) {
        this.employeeUserId = employeeUserId;
        this.schoolId = schoolId;
        this.paoId = paoId;
        this.status = ClaimStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Employee side
    // ------------------------------------------------------------------

    boolean isEditable() {
        return EDITABLE.contains(status);
    }

    /** Replaces the employee entered content. Only allowed while editable. */
    void updateContent(ClaimContent c, Instant now) {
        require(isEditable(), "This claim can no longer be edited");
        this.patientName = c.patientName();
        this.patientRelation = c.patientRelation();
        this.dependentId = c.dependentId();
        this.illnessDescription = c.illnessDescription();
        this.treatmentType = c.treatmentType();
        this.treatmentFrom = c.treatmentFrom();
        this.treatmentTo = c.treatmentTo();
        this.admissionDate = c.admissionDate();
        this.dischargeDate = c.dischargeDate();
        this.hospitalName = c.hospitalName();
        this.hospitalAddress = c.hospitalAddress();
        this.hospitalType = c.hospitalType();
        this.emergency = c.emergency();
        this.referralDetails = c.referralDetails();
        this.medicalAdvanceDetails = c.medicalAdvanceDetails();
        this.items.clear();
        this.items.addAll(c.items());
        // Reconcile instead of clear and re-add: Hibernate flushes inserts before
        // deletes, which would trip the (claim, document) unique constraint
        Set<UUID> wanted = c.attachments().stream().map(ClaimAttachment::getDocumentId)
                .collect(Collectors.toSet());
        this.attachments.removeIf(a -> !wanted.contains(a.getDocumentId()));
        Set<UUID> present = this.attachments.stream().map(ClaimAttachment::getDocumentId)
                .collect(Collectors.toSet());
        c.attachments().stream().filter(a -> !present.contains(a.getDocumentId())).forEach(this.attachments::add);
        this.claimedAmount = sum(ClaimItem::getAmountClaimed);
        // Any earlier school or PAO figures no longer apply to changed content
        this.restrictedAmount = null;
        this.admittedAmount = null;
        this.auditRecommendation = null;
        this.updatedAt = now;
    }

    /**
     * First submission or resubmission after a return. The claim always goes
     * to the Head of School, because the school certificate and calculation
     * sheet must describe the corrected content. Seniority is kept.
     */
    ClaimStatus submit(Long userId, String number, String financialYear, Instant now) {
        requireOwner(userId);
        require(isEditable(), "This claim has already been submitted");
        ClaimStatus from = status;
        if (firstSubmittedAt == null) {
            this.firstSubmittedAt = now;
            this.claimNumber = number;
            this.financialYear = financialYear;
        }
        this.undertakingAccepted = true;
        this.undertakingAcceptedAt = now;
        this.lastSubmittedAt = now;
        moveTo(ClaimStatus.PENDING_HOS, null, now);
        return from;
    }

    ClaimStatus withdraw(Long userId, Instant now) {
        requireOwner(userId);
        require(status == ClaimStatus.RETURNED_BY_HOS || status == ClaimStatus.RETURNED_BY_PAO
                        || (status == ClaimStatus.PENDING_HOS && assignedTo == null),
                "This claim cannot be withdrawn at its current stage");
        ClaimStatus from = status;
        moveTo(ClaimStatus.WITHDRAWN, null, now);
        return from;
    }

    // ------------------------------------------------------------------
    // Queue handling (all reviewer stages)
    // ------------------------------------------------------------------

    void take(Long userId, Instant now) {
        require(QUEUED.contains(status), "This claim is not waiting in a queue");
        require(assignedTo == null || assignedTo.equals(userId), "Another official is working on this claim");
        this.assignedTo = userId;
        this.updatedAt = now;
    }

    void release(Long userId, Instant now) {
        requireAssignee(userId);
        this.assignedTo = null;
        this.updatedAt = now;
    }

    /** Time limit passed while held: back to the queue so a colleague can take it. */
    boolean releaseOverdue(ClaimStatus stage, Instant stageEntered, Instant now) {
        if (status != stage || assignedTo == null || stageEnteredAt == null || !stageEnteredAt.equals(stageEntered)) {
            return false;
        }
        this.assignedTo = null;
        this.updatedAt = now;
        return true;
    }

    // ------------------------------------------------------------------
    // Head of School
    // ------------------------------------------------------------------

    /** Calculation sheet filled and certificate signed: forward to the PAO. */
    ClaimStatus forwardToPao(Long hosUserId, RateBasis basis, Instant now) {
        require(status == ClaimStatus.PENDING_HOS, "This claim is not with the Head of School");
        requireAssignee(hosUserId);
        require(items.stream().allMatch(i -> i.getAmountRestricted() != null),
                "Fill the restricted amount for every item");
        this.restrictedAmount = sum(ClaimItem::getAmountRestricted);
        this.rateBasis = basis;
        this.hosCertifiedBy = hosUserId;
        this.hosCertifiedAt = now;
        ClaimStatus from = status;
        moveTo(ClaimStatus.PENDING_PAO_AUDIT, null, now);
        return from;
    }

    ClaimStatus returnByHos(Long hosUserId, Instant now) {
        require(status == ClaimStatus.PENDING_HOS, "This claim is not with the Head of School");
        requireAssignee(hosUserId);
        ClaimStatus from = status;
        this.returnCount++;
        moveTo(ClaimStatus.RETURNED_BY_HOS, null, now);
        return from;
    }

    // ------------------------------------------------------------------
    // PAO auditor (maker)
    // ------------------------------------------------------------------

    ClaimStatus recommend(Long auditorUserId, Recommendation recommendation, Instant now) {
        require(status == ClaimStatus.PENDING_PAO_AUDIT, "This claim is not with the PAO auditor");
        requireAssignee(auditorUserId);
        require(items.stream().allMatch(i -> i.getAmountAdmitted() != null),
                "Fill the admitted amount for every item");
        this.admittedAmount = sum(ClaimItem::getAmountAdmitted);
        require(recommendation == Recommendation.REJECT || admittedAmount.signum() > 0,
                "Nothing is admitted. Recommend rejection with reasons instead");
        this.auditRecommendation = recommendation;
        this.auditedBy = auditorUserId;
        this.auditedAt = now;
        ClaimStatus from = status;
        moveTo(ClaimStatus.PENDING_SANCTION, null, now);
        return from;
    }

    ClaimStatus returnByPao(Long auditorUserId, Instant now) {
        require(status == ClaimStatus.PENDING_PAO_AUDIT, "This claim is not with the PAO auditor");
        requireAssignee(auditorUserId);
        ClaimStatus from = status;
        this.returnCount++;
        moveTo(ClaimStatus.RETURNED_BY_PAO, null, now);
        return from;
    }

    // ------------------------------------------------------------------
    // PAO officer (checker)
    // ------------------------------------------------------------------

    ClaimStatus sanction(Long officerUserId, Instant now) {
        require(status == ClaimStatus.PENDING_SANCTION, "This claim is not awaiting sanction");
        requireAssignee(officerUserId);
        requireSeparationOfDuties(officerUserId);
        require(admittedAmount != null && admittedAmount.signum() > 0, "There is no admitted amount to sanction");
        this.sanctionedBy = officerUserId;
        this.sanctionedAt = now;
        ClaimStatus from = status;
        moveTo(ClaimStatus.SANCTIONED, null, now);
        return from;
    }

    /** Officer disagrees with the scrutiny: back to the same auditor. */
    ClaimStatus sendBackToAudit(Long officerUserId, Instant now) {
        require(status == ClaimStatus.PENDING_SANCTION, "This claim is not awaiting sanction");
        requireAssignee(officerUserId);
        ClaimStatus from = status;
        moveTo(ClaimStatus.PENDING_PAO_AUDIT, auditedBy, now);
        return from;
    }

    ClaimStatus reject(Long officerUserId, String reason, Instant now) {
        require(status == ClaimStatus.PENDING_SANCTION, "This claim is not awaiting sanction");
        requireAssignee(officerUserId);
        requireSeparationOfDuties(officerUserId);
        this.rejectionReason = reason;
        ClaimStatus from = status;
        moveTo(ClaimStatus.REJECTED, null, now);
        return from;
    }

    ClaimStatus markPaid(String batchRef, Instant now) {
        require(status == ClaimStatus.SANCTIONED, "Only sanctioned claims can be paid");
        this.paidAt = now;
        this.paymentBatchRef = batchRef;
        ClaimStatus from = status;
        moveTo(ClaimStatus.PAID, null, now);
        return from;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void moveTo(ClaimStatus next, Long assignee, Instant now) {
        this.status = next;
        this.assignedTo = assignee;
        this.stageEnteredAt = now;
        this.updatedAt = now;
    }

    private BigDecimal sum(Function<ClaimItem, BigDecimal> field) {
        return items.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireOwner(Long userId) {
        require(employeeUserId.equals(userId), "Not your claim");
    }

    private void requireAssignee(Long userId) {
        require(userId.equals(assignedTo), "Take this claim from the queue before acting on it");
    }

    /** The person who scrutinised a claim may not also sanction or reject it. */
    private void requireSeparationOfDuties(Long officerUserId) {
        require(!officerUserId.equals(auditedBy), "The auditor of a claim cannot also sanction it");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessRuleException("INVALID_STATE", message);
        }
    }

    Long getId() { return id; }
    String getClaimNumber() { return claimNumber; }
    Long getEmployeeUserId() { return employeeUserId; }
    Long getSchoolId() { return schoolId; }
    Long getPaoId() { return paoId; }
    String getPatientName() { return patientName; }
    Relation getPatientRelation() { return patientRelation; }
    Long getDependentId() { return dependentId; }
    String getIllnessDescription() { return illnessDescription; }
    TreatmentType getTreatmentType() { return treatmentType; }
    LocalDate getTreatmentFrom() { return treatmentFrom; }
    LocalDate getTreatmentTo() { return treatmentTo; }
    LocalDate getAdmissionDate() { return admissionDate; }
    LocalDate getDischargeDate() { return dischargeDate; }
    String getHospitalName() { return hospitalName; }
    String getHospitalAddress() { return hospitalAddress; }
    HospitalType getHospitalType() { return hospitalType; }
    boolean isEmergency() { return emergency; }
    String getReferralDetails() { return referralDetails; }
    String getMedicalAdvanceDetails() { return medicalAdvanceDetails; }
    ClaimStatus getStatus() { return status; }
    BigDecimal getClaimedAmount() { return claimedAmount; }
    BigDecimal getRestrictedAmount() { return restrictedAmount; }
    BigDecimal getAdmittedAmount() { return admittedAmount; }
    boolean isUndertakingAccepted() { return undertakingAccepted; }
    Instant getUndertakingAcceptedAt() { return undertakingAcceptedAt; }
    String getFinancialYear() { return financialYear; }
    Instant getFirstSubmittedAt() { return firstSubmittedAt; }
    Instant getLastSubmittedAt() { return lastSubmittedAt; }
    Instant getStageEnteredAt() { return stageEnteredAt; }
    Long getAssignedTo() { return assignedTo; }
    int getReturnCount() { return returnCount; }
    Long getHosCertifiedBy() { return hosCertifiedBy; }
    Instant getHosCertifiedAt() { return hosCertifiedAt; }
    RateBasis getRateBasis() { return rateBasis; }

    /**
     * The basis to suggest before the school has chosen one: government
     * hospitals as billed, non empanelled private hospitals at Non-NABH
     * rates (CGHS memorandum of 03.10.2025), empanelled hospitals at NABH.
     */
    RateBasis suggestedRateBasis() {
        if (rateBasis != null) {
            return rateBasis;
        }
        return switch (hospitalType) {
            case GOVERNMENT -> RateBasis.AS_BILLED;
            case PRIVATE -> RateBasis.NON_NABH;
            case EMPANELLED -> RateBasis.NABH;
        };
    }
    Long getAuditedBy() { return auditedBy; }
    Instant getAuditedAt() { return auditedAt; }
    Recommendation getAuditRecommendation() { return auditRecommendation; }
    Long getSanctionedBy() { return sanctionedBy; }
    Instant getSanctionedAt() { return sanctionedAt; }
    Instant getPaidAt() { return paidAt; }
    String getPaymentBatchRef() { return paymentBatchRef; }
    String getRejectionReason() { return rejectionReason; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    List<ClaimItem> getItems() { return items; }
    List<ClaimAttachment> getAttachments() { return attachments; }

    /** Validated content handed over by the service. */
    record ClaimContent(
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
            List<ClaimItem> items,
            List<ClaimAttachment> attachments) {
    }
}
