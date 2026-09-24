package com.mrms.claim.internal;

import com.mrms.claim.ClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Timeline entry shown to the employee and to reviewers: who did what,
 * when, and why. Complements the tamper evident audit trail.
 */
@Entity
@Immutable
@Table(name = "claim_event")
class ClaimEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ClaimStatus toStatus;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    private String remarks;

    @Column(name = "reason_codes")
    private String reasonCodes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClaimEvent() {
    }

    ClaimEvent(Long claimId, String action, ClaimStatus fromStatus, ClaimStatus toStatus, Long actorUserId,
               String actorName, String actorRole, String remarks, String reasonCodes, Instant occurredAt) {
        this.claimId = claimId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.remarks = remarks;
        this.reasonCodes = reasonCodes;
        this.occurredAt = occurredAt;
    }

    Long getId() { return id; }
    String getAction() { return action; }
    ClaimStatus getFromStatus() { return fromStatus; }
    ClaimStatus getToStatus() { return toStatus; }
    String getActorName() { return actorName; }
    String getActorRole() { return actorRole; }
    String getRemarks() { return remarks; }
    String getReasonCodes() { return reasonCodes; }
    Instant getOccurredAt() { return occurredAt; }
}
