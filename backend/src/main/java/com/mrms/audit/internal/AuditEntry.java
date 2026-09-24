package com.mrms.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One audit record. Marked immutable: Hibernate will never issue an UPDATE
 * for it. In production the database role used by the application should
 * also be denied UPDATE and DELETE on this table.
 */
@Entity
@Immutable
@Table(name = "audit_entry")
class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "prev_hash", nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    protected AuditEntry() {
    }

    AuditEntry(Instant occurredAt, Long actorUserId, String actorUsername, String actorRole, String action,
               String entityType, String entityId, String details, String ipAddress, String prevHash) {
        this.occurredAt = occurredAt;
        this.actorUserId = actorUserId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.prevHash = prevHash;
        this.hash = AuditHasher.hash(this);
    }

    /**
     * The exact text that is hashed. Changing this breaks verification of
     * existing chains, so treat the format as frozen.
     */
    String canonical() {
        return String.join("|",
                prevHash,
                occurredAt.toString(),
                str(actorUserId),
                str(actorUsername),
                str(actorRole),
                action,
                entityType,
                str(entityId),
                str(details),
                str(ipAddress));
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    Long getId() {
        return id;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }

    Long getActorUserId() {
        return actorUserId;
    }

    String getActorUsername() {
        return actorUsername;
    }

    String getActorRole() {
        return actorRole;
    }

    String getAction() {
        return action;
    }

    String getEntityType() {
        return entityType;
    }

    String getEntityId() {
        return entityId;
    }

    String getDetails() {
        return details;
    }

    String getIpAddress() {
        return ipAddress;
    }

    String getPrevHash() {
        return prevHash;
    }

    String getHash() {
        return hash;
    }
}
