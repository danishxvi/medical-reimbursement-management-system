package com.mrms.escalation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Entities and repositories of the escalation module. */
final class SlaEntities {

    private SlaEntities() {
    }

    enum Level { REMINDER, BREACH, ESCALATED }

    /** One step reached by one record in one stage. Never deleted: it is the performance record. */
    @Entity(name = "SlaEvent")
    @Table(name = "sla_event")
    static class SlaEvent {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "subject_type", nullable = false)
        private String subjectType;

        @Column(name = "subject_id", nullable = false)
        private Long subjectId;

        @Column(nullable = false)
        private String reference;

        @Column(nullable = false)
        private String stage;

        @Column(name = "stage_entered_at", nullable = false)
        private Instant stageEnteredAt;

        @Column(nullable = false)
        private String level;

        @Column(name = "office_type", nullable = false)
        private String officeType;

        @Column(name = "office_id", nullable = false)
        private Long officeId;

        private String zone;

        @Column(name = "held_by")
        private Long heldBy;

        @Column(name = "sla_days", nullable = false)
        private int slaDays;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        @Column(name = "resolved_at")
        private Instant resolvedAt;

        protected SlaEvent() {
        }

        SlaEvent(String subjectType, Long subjectId, String reference, String stage, Instant stageEnteredAt,
                 Level level, String officeType, Long officeId, String zone, Long heldBy, int slaDays, Instant now) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
            this.reference = reference;
            this.stage = stage;
            this.stageEnteredAt = stageEnteredAt;
            this.level = level.name();
            this.officeType = officeType;
            this.officeId = officeId;
            this.zone = zone;
            this.heldBy = heldBy;
            this.slaDays = slaDays;
            this.occurredAt = now;
        }

        void resolve(Instant now) {
            this.resolvedAt = now;
        }

        String key() {
            return subjectType + "|" + subjectId + "|" + stage + "|" + stageEnteredAt;
        }

        String getLevel() { return level; }
        String getZone() { return zone; }
        String getOfficeType() { return officeType; }
        Long getOfficeId() { return officeId; }
        String getStage() { return stage; }
        Instant getResolvedAt() { return resolvedAt; }
    }

    /** A reminder sent by hand from the oversight screen. */
    @Entity(name = "SlaNudge")
    @Table(name = "sla_nudge")
    static class SlaNudge {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "subject_type", nullable = false)
        private String subjectType;

        @Column(name = "subject_id", nullable = false)
        private Long subjectId;

        @Column(nullable = false)
        private String stage;

        @Column(name = "by_user_id", nullable = false)
        private Long byUserId;

        @Column(name = "occurred_at", nullable = false)
        private Instant occurredAt;

        protected SlaNudge() {
        }

        SlaNudge(String subjectType, Long subjectId, String stage, Long byUserId, Instant now) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
            this.stage = stage;
            this.byUserId = byUserId;
            this.occurredAt = now;
        }

        Instant getOccurredAt() { return occurredAt; }
    }
}
