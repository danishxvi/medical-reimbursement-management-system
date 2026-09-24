package com.mrms.budget.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** Entities of the budget module. Allocations and payment batches are immutable records. */
final class BudgetEntities {

    private BudgetEntities() {
    }

    @Entity(name = "BudgetAllocation")
    @Immutable
    @Table(name = "budget_allocation")
    static class BudgetAllocation {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "school_id", nullable = false)
        private Long schoolId;

        @Column(name = "financial_year", nullable = false)
        private String financialYear;

        @Column(nullable = false, precision = 14, scale = 2)
        private BigDecimal amount;

        @Column(name = "sanction_order_no", nullable = false)
        private String sanctionOrderNo;

        private String remarks;

        @Column(name = "allocated_by", nullable = false)
        private Long allocatedBy;

        @Column(name = "allocated_at", nullable = false)
        private Instant allocatedAt;

        protected BudgetAllocation() {
        }

        BudgetAllocation(Long schoolId, String financialYear, BigDecimal amount, String sanctionOrderNo,
                         String remarks, Long allocatedBy, Instant allocatedAt) {
            this.schoolId = schoolId;
            this.financialYear = financialYear;
            this.amount = amount;
            this.sanctionOrderNo = sanctionOrderNo;
            this.remarks = remarks;
            this.allocatedBy = allocatedBy;
            this.allocatedAt = allocatedAt;
        }

        Long getId() { return id; }
        Long getSchoolId() { return schoolId; }
        String getFinancialYear() { return financialYear; }
        BigDecimal getAmount() { return amount; }
        String getSanctionOrderNo() { return sanctionOrderNo; }
        String getRemarks() { return remarks; }
        Long getAllocatedBy() { return allocatedBy; }
        Instant getAllocatedAt() { return allocatedAt; }
    }

    enum DemandStatus { RAISED, ACKNOWLEDGED, SUPERSEDED }

    @Entity(name = "BudgetDemand")
    @Table(name = "budget_demand")
    static class BudgetDemand {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "school_id", nullable = false)
        private Long schoolId;

        @Column(name = "pao_id", nullable = false)
        private Long paoId;

        @Column(name = "financial_year", nullable = false)
        private String financialYear;

        @Column(nullable = false, precision = 14, scale = 2)
        private BigDecimal amount;

        @Column(name = "claim_count", nullable = false)
        private int claimCount;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private DemandStatus status;

        @Column(name = "raised_by", nullable = false)
        private Long raisedBy;

        @Column(name = "raised_at", nullable = false)
        private Instant raisedAt;

        @Column(name = "acknowledged_by")
        private Long acknowledgedBy;

        @Column(name = "acknowledged_at")
        private Instant acknowledgedAt;

        protected BudgetDemand() {
        }

        BudgetDemand(Long schoolId, Long paoId, String financialYear, BigDecimal amount, int claimCount,
                     Long raisedBy, Instant raisedAt) {
            this.schoolId = schoolId;
            this.paoId = paoId;
            this.financialYear = financialYear;
            this.amount = amount;
            this.claimCount = claimCount;
            this.status = DemandStatus.RAISED;
            this.raisedBy = raisedBy;
            this.raisedAt = raisedAt;
        }

        void supersede() {
            this.status = DemandStatus.SUPERSEDED;
        }

        void acknowledge(Long userId, Instant now) {
            this.status = DemandStatus.ACKNOWLEDGED;
            this.acknowledgedBy = userId;
            this.acknowledgedAt = now;
        }

        Long getId() { return id; }
        Long getSchoolId() { return schoolId; }
        Long getPaoId() { return paoId; }
        String getFinancialYear() { return financialYear; }
        BigDecimal getAmount() { return amount; }
        int getClaimCount() { return claimCount; }
        DemandStatus getStatus() { return status; }
        Long getRaisedBy() { return raisedBy; }
        Instant getRaisedAt() { return raisedAt; }
        Long getAcknowledgedBy() { return acknowledgedBy; }
        Instant getAcknowledgedAt() { return acknowledgedAt; }
    }

    @Entity(name = "PaymentBatch")
    @Immutable
    @Table(name = "payment_batch")
    static class PaymentBatch {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "batch_ref", nullable = false, unique = true)
        private String batchRef;

        @Column(name = "school_id", nullable = false)
        private Long schoolId;

        @Column(name = "financial_year", nullable = false)
        private String financialYear;

        @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
        private BigDecimal totalAmount;

        @Column(name = "claim_count", nullable = false)
        private int claimCount;

        @Column(name = "created_by", nullable = false)
        private Long createdBy;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected PaymentBatch() {
        }

        PaymentBatch(String batchRef, Long schoolId, String financialYear, BigDecimal totalAmount, int claimCount,
                     Long createdBy, Instant createdAt) {
            this.batchRef = batchRef;
            this.schoolId = schoolId;
            this.financialYear = financialYear;
            this.totalAmount = totalAmount;
            this.claimCount = claimCount;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
        }

        Long getId() { return id; }
        String getBatchRef() { return batchRef; }
        Long getSchoolId() { return schoolId; }
        String getFinancialYear() { return financialYear; }
        BigDecimal getTotalAmount() { return totalAmount; }
        int getClaimCount() { return claimCount; }
        Long getCreatedBy() { return createdBy; }
        Instant getCreatedAt() { return createdAt; }
    }
}
