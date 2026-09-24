package com.mrms.enac.internal;

import com.mrms.enac.NacTypes.NacStatus;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root of an e-NAC. All state changes go through methods that
 * check the current status, so an illegal transition cannot be persisted.
 */
@Entity
@Table(name = "nac_request")
class NacRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nac_number", unique = true)
    private String nacNumber;

    @Column(name = "employee_user_id", nullable = false)
    private Long employeeUserId;

    @Column(name = "dispensary_id", nullable = false)
    private Long dispensaryId;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_relation", nullable = false)
    private Relation patientRelation;

    @Column(name = "dependent_id")
    private Long dependentId;

    @Column(name = "prescription_date", nullable = false)
    private LocalDate prescriptionDate;

    @Column(name = "prescribed_by", nullable = false)
    private String prescribedBy;

    @Column(name = "prescription_document_id", nullable = false)
    private UUID prescriptionDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NacStatus status;

    /** Seniority in the dispensary queue; never reset on resubmission. */
    @Column(name = "queue_since", nullable = false)
    private Instant queueSince;

    @Column(name = "stage_entered_at", nullable = false)
    private Instant stageEnteredAt;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "pharmacist_id")
    private Long pharmacistId;

    @Column(name = "pharmacist_at")
    private Instant pharmacistAt;

    @Column(name = "pharmacist_remarks")
    private String pharmacistRemarks;

    @Column(name = "medical_officer_id")
    private Long medicalOfficerId;

    @Column(name = "medical_officer_at")
    private Instant medicalOfficerAt;

    @Column(name = "medical_officer_remarks")
    private String medicalOfficerRemarks;

    @Column(name = "return_remarks")
    private String returnRemarks;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "nac_request_id", nullable = false)
    @OrderBy("lineNo")
    private List<NacItem> items = new ArrayList<>();

    protected NacRequest() {
    }

    NacRequest(Long employeeUserId, Long dispensaryId, String patientName, Relation patientRelation, Long dependentId,
               LocalDate prescriptionDate, String prescribedBy, UUID prescriptionDocumentId, List<NacItem> items,
               Instant now) {
        this.employeeUserId = employeeUserId;
        this.dispensaryId = dispensaryId;
        this.patientName = patientName;
        this.patientRelation = patientRelation;
        this.dependentId = dependentId;
        this.prescriptionDate = prescriptionDate;
        this.prescribedBy = prescribedBy;
        this.prescriptionDocumentId = prescriptionDocumentId;
        this.items.addAll(items);
        this.status = NacStatus.PENDING_PHARMACIST;
        this.queueSince = now;
        this.stageEnteredAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ------------------------------------------------------------------
    // Transitions
    // ------------------------------------------------------------------

    void assignTo(Long userId, Instant now) {
        require(status == NacStatus.PENDING_PHARMACIST || status == NacStatus.PENDING_MEDICAL_OFFICER,
                "This request is not waiting in a queue");
        this.assignedTo = userId;
        this.updatedAt = now;
    }

    void release(Long userId, Instant now) {
        requireAssignee(userId);
        this.assignedTo = null;
        this.updatedAt = now;
    }

    /** Pharmacist has marked every item: forward to the Medical Officer. */
    void completePharmacistReview(Long pharmacistUserId, String remarks, Instant now) {
        require(status == NacStatus.PENDING_PHARMACIST, "This request is not with the pharmacist");
        requireAssignee(pharmacistUserId);
        require(items.stream().allMatch(i -> i.getDecision() != null), "Every item needs a decision");
        this.pharmacistId = pharmacistUserId;
        this.pharmacistAt = now;
        this.pharmacistRemarks = remarks;
        moveTo(NacStatus.PENDING_MEDICAL_OFFICER, null, now);
    }

    /** Prescription cannot be processed (for example illegible): back to the employee. */
    void returnToEmployee(Long pharmacistUserId, String remarks, Instant now) {
        require(status == NacStatus.PENDING_PHARMACIST, "This request is not with the pharmacist");
        requireAssignee(pharmacistUserId);
        this.returnRemarks = remarks;
        items.forEach(NacItem::clearDecision);
        moveTo(NacStatus.RETURNED, null, now);
    }

    /** Medical Officer countersigns: the certificate is issued. */
    void countersign(Long moUserId, String remarks, String number, Instant now) {
        require(status == NacStatus.PENDING_MEDICAL_OFFICER, "This request is not with the Medical Officer");
        requireAssignee(moUserId);
        this.medicalOfficerId = moUserId;
        this.medicalOfficerAt = now;
        this.medicalOfficerRemarks = remarks;
        this.nacNumber = number;
        this.issuedAt = now;
        moveTo(NacStatus.ISSUED, null, now);
    }

    /**
     * Medical Officer disagrees: back to the same pharmacist, who is
     * accountable for correcting their own decisions.
     */
    void sendBackToPharmacist(Long moUserId, String remarks, Instant now) {
        require(status == NacStatus.PENDING_MEDICAL_OFFICER, "This request is not with the Medical Officer");
        requireAssignee(moUserId);
        this.medicalOfficerRemarks = remarks;
        moveTo(NacStatus.PENDING_PHARMACIST, pharmacistId, now);
    }

    /** Employee corrects a returned request; it keeps its original queue position. */
    void resubmit(Long employeeId, LocalDate prescriptionDate, String prescribedBy, UUID prescriptionDocumentId,
                  List<NacItem> newItems, Instant now) {
        require(status == NacStatus.RETURNED, "Only a returned request can be resubmitted");
        require(employeeUserId.equals(employeeId), "Not your request");
        this.prescriptionDate = prescriptionDate;
        this.prescribedBy = prescribedBy;
        this.prescriptionDocumentId = prescriptionDocumentId;
        this.items.clear();
        this.items.addAll(newItems);
        moveTo(NacStatus.PENDING_PHARMACIST, null, now);
    }

    private void moveTo(NacStatus next, Long assignee, Instant now) {
        this.status = next;
        this.assignedTo = assignee;
        this.stageEnteredAt = now;
        this.updatedAt = now;
    }

    private void requireAssignee(Long userId) {
        require(userId.equals(assignedTo), "Take this request from the queue before acting on it");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessRuleException("INVALID_STATE", message);
        }
    }

    Long getId() { return id; }
    String getNacNumber() { return nacNumber; }
    Long getEmployeeUserId() { return employeeUserId; }
    Long getDispensaryId() { return dispensaryId; }
    String getPatientName() { return patientName; }
    Relation getPatientRelation() { return patientRelation; }
    Long getDependentId() { return dependentId; }
    LocalDate getPrescriptionDate() { return prescriptionDate; }
    String getPrescribedBy() { return prescribedBy; }
    UUID getPrescriptionDocumentId() { return prescriptionDocumentId; }
    NacStatus getStatus() { return status; }
    Instant getQueueSince() { return queueSince; }
    Instant getStageEnteredAt() { return stageEnteredAt; }
    Long getAssignedTo() { return assignedTo; }
    Long getPharmacistId() { return pharmacistId; }
    Instant getPharmacistAt() { return pharmacistAt; }
    String getPharmacistRemarks() { return pharmacistRemarks; }
    Long getMedicalOfficerId() { return medicalOfficerId; }
    Instant getMedicalOfficerAt() { return medicalOfficerAt; }
    String getMedicalOfficerRemarks() { return medicalOfficerRemarks; }
    String getReturnRemarks() { return returnRemarks; }
    Instant getIssuedAt() { return issuedAt; }
    Instant getCreatedAt() { return createdAt; }
    List<NacItem> getItems() { return items; }
}
