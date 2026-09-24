package com.mrms.budget.internal;

import com.mrms.budget.internal.BudgetEntities.BudgetAllocation;
import com.mrms.budget.internal.BudgetEntities.BudgetDemand;
import com.mrms.budget.internal.BudgetEntities.DemandStatus;
import com.mrms.budget.internal.BudgetEntities.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

interface AllocationRepository extends JpaRepository<BudgetAllocation, Long> {

    List<BudgetAllocation> findBySchoolIdAndFinancialYearOrderByAllocatedAtDesc(Long schoolId, String fy);

    @Query("select coalesce(sum(a.amount), 0) from BudgetAllocation a "
            + "where a.schoolId = :schoolId and a.financialYear = :fy")
    BigDecimal totalFor(@Param("schoolId") Long schoolId, @Param("fy") String fy);
}

interface DemandRepository extends JpaRepository<BudgetDemand, Long> {

    List<BudgetDemand> findBySchoolIdOrderByRaisedAtDesc(Long schoolId);

    List<BudgetDemand> findByPaoIdOrderByRaisedAtDesc(Long paoId);

    List<BudgetDemand> findBySchoolIdAndFinancialYearAndStatus(Long schoolId, String fy, DemandStatus status);

    long countByPaoIdAndStatus(Long paoId, DemandStatus status);
}

interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {

    List<PaymentBatch> findBySchoolIdOrderByCreatedAtDesc(Long schoolId);

    @Query("select coalesce(sum(b.totalAmount), 0) from PaymentBatch b "
            + "where b.schoolId = :schoolId and b.financialYear = :fy")
    BigDecimal paidFor(@Param("schoolId") Long schoolId, @Param("fy") String fy);

    @Query(value = "select nextval('payment_batch_seq')", nativeQuery = true)
    long nextNumber();
}
