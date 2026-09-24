package com.mrms.budget.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.budget.BudgetQueries;
import com.mrms.budget.internal.BudgetEntities.BudgetAllocation;
import com.mrms.budget.internal.BudgetEntities.BudgetDemand;
import com.mrms.budget.internal.BudgetEntities.DemandStatus;
import com.mrms.budget.internal.BudgetEntities.PaymentBatch;
import com.mrms.claim.ClaimPayments;
import com.mrms.claim.ClaimPayments.PayableClaim;
import com.mrms.claim.ClaimPayments.SchoolPipeline;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.organisation.OrganisationViews.SchoolRef;
import com.mrms.shared.domain.FinancialYear;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.ForbiddenException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
class BudgetService implements BudgetQueries {

    private final AllocationRepository allocations;
    private final DemandRepository demands;
    private final PaymentBatchRepository batches;
    private final ClaimPayments claims;
    private final OrganisationDirectory organisation;
    private final Accounts accounts;
    private final AuditTrail audit;
    private final Clock clock;

    BudgetService(AllocationRepository allocations, DemandRepository demands, PaymentBatchRepository batches,
                  ClaimPayments claims, OrganisationDirectory organisation, Accounts accounts, AuditTrail audit,
                  Clock clock) {
        this.allocations = allocations;
        this.demands = demands;
        this.batches = batches;
        this.claims = claims;
        this.organisation = organisation;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    // ==================================================================
    // Position (read)
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public SchoolPosition position(Long schoolId, String fy) {
        BigDecimal allocated = allocations.totalFor(schoolId, fy);
        BigDecimal paid = batches.paidFor(schoolId, fy);
        BigDecimal balance = allocated.subtract(paid);
        SchoolPipeline pipeline = claims.pipeline(schoolId);
        BigDecimal need = pipeline.pendingAmount().add(pipeline.sanctionedAmount()).subtract(balance);
        return new SchoolPosition(schoolId, fy, allocated, paid, balance, pipeline.pendingCount(),
                pipeline.pendingAmount(), pipeline.sanctionedCount(), pipeline.sanctionedAmount(),
                need.signum() > 0 ? need : BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public long openDemands(Long paoId) {
        return demands.countByPaoIdAndStatus(paoId, DemandStatus.RAISED);
    }

    // ==================================================================
    // Head of School
    // ==================================================================

    @Transactional(readOnly = true)
    Map<String, Object> schoolOverview() {
        MrmsPrincipal me = requireRole(Role.HOS);
        String fy = FinancialYear.current();
        return Map.of(
                "position", position(me.schoolId(), fy),
                "demands", demandViews(demands.findBySchoolIdOrderByRaisedAtDesc(me.schoolId())),
                "allocations", allocationViews(allocations.findBySchoolIdAndFinancialYearOrderByAllocatedAtDesc(
                        me.schoolId(), fy)));
    }

    /**
     * The demand amount is computed by the server from real claims; the
     * school cannot type in an arbitrary figure. A new demand replaces any
     * earlier one that the PAO has not yet acknowledged.
     */
    @Transactional
    DemandView raiseDemand() {
        MrmsPrincipal me = requireRole(Role.HOS);
        SchoolRef school = organisation.school(me.schoolId()).orElseThrow(() -> new NotFoundException("School"));
        String fy = FinancialYear.current();
        SchoolPosition position = position(school.id(), fy);
        if (position.suggestedDemand().signum() <= 0) {
            throw new BusinessRuleException("NO_DEMAND",
                    "The current balance already covers every claim in the pipeline");
        }
        demands.findBySchoolIdAndFinancialYearAndStatus(school.id(), fy, DemandStatus.RAISED)
                .forEach(BudgetDemand::supersede);
        int claimCount = (int) (position.pendingCount() + position.awaitingFundsCount());
        BudgetDemand demand = demands.save(new BudgetDemand(school.id(), school.paoId(), fy,
                position.suggestedDemand(), claimCount, me.userId(), clock.instant()));
        audit.record("BUDGET_DEMAND_RAISED", "SCHOOL", school.id(), fy + " amount " + demand.getAmount());
        return demandViews(List.of(demand)).getFirst();
    }

    // ==================================================================
    // PAO
    // ==================================================================

    @Transactional(readOnly = true)
    List<SchoolPosition> paoSchools() {
        MrmsPrincipal me = requirePao();
        String fy = FinancialYear.current();
        return organisation.schoolsOfPao(me.paoId()).stream().map(s -> position(s.id(), fy)).toList();
    }

    @Transactional(readOnly = true)
    List<DemandView> paoDemands() {
        MrmsPrincipal me = requirePao();
        return demandViews(demands.findByPaoIdOrderByRaisedAtDesc(me.paoId()));
    }

    @Transactional
    void acknowledgeDemand(Long demandId) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        BudgetDemand demand = demands.findById(demandId).filter(d -> d.getPaoId().equals(me.paoId()))
                .orElseThrow(() -> new NotFoundException("Demand"));
        if (demand.getStatus() != DemandStatus.RAISED) {
            throw new BusinessRuleException("INVALID_STATE", "Only an open demand can be acknowledged");
        }
        demand.acknowledge(me.userId(), clock.instant());
        audit.record("BUDGET_DEMAND_ACKNOWLEDGED", "SCHOOL", demand.getSchoolId(), "Demand " + demandId);
    }

    @Transactional(readOnly = true)
    Map<String, Object> schoolDetail(Long schoolId) {
        MrmsPrincipal me = requirePao();
        SchoolRef school = schoolOfMyPao(me, schoolId);
        String fy = FinancialYear.current();
        return Map.of(
                "school", school,
                "position", position(schoolId, fy),
                "allocations", allocationViews(allocations.findBySchoolIdAndFinancialYearOrderByAllocatedAtDesc(
                        schoolId, fy)),
                "payable", claims.sanctionedAwaitingPayment(schoolId),
                "batches", batches.findBySchoolIdOrderByCreatedAtDesc(schoolId).stream()
                        .map(b -> new BatchView(b.getBatchRef(), b.getFinancialYear(), b.getTotalAmount(),
                                b.getClaimCount(), b.getCreatedAt()))
                        .toList());
    }

    @Transactional
    void allocate(Long schoolId, BigDecimal amount, String orderNo, String remarks, String financialYear) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        schoolOfMyPao(me, schoolId);
        String fy = financialYear == null || financialYear.isBlank() ? FinancialYear.current() : financialYear;
        if (!FinancialYear.isValid(fy)) {
            throw new BusinessRuleException("INVALID_FY", "Financial year must look like 2026-27");
        }
        BudgetAllocation allocation = allocations.save(new BudgetAllocation(schoolId, fy, amount, orderNo.trim(),
                remarks == null || remarks.isBlank() ? null : remarks.trim(), me.userId(), clock.instant()));
        audit.record("BUDGET_ALLOCATED", "SCHOOL", schoolId,
                fy + " amount " + amount + " order " + allocation.getSanctionOrderNo());
    }

    /**
     * Pays sanctioned claims of a school strictly in seniority order. The run
     * stops at the first claim the balance cannot cover, so a smaller, newer
     * claim can never overtake an older one.
     */
    @Transactional
    BatchView runPayments(Long schoolId, String password) {
        MrmsPrincipal me = requireRole(Role.PAO_OFFICER);
        accounts.confirmPassword(password);
        SchoolRef school = schoolOfMyPao(me, schoolId);
        String fy = FinancialYear.current();
        BigDecimal balance = position(schoolId, fy).balance();

        List<Long> toPay = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PayableClaim claim : claims.sanctionedAwaitingPayment(schoolId)) {
            if (total.add(claim.admittedAmount()).compareTo(balance) > 0) {
                break;
            }
            total = total.add(claim.admittedAmount());
            toPay.add(claim.claimId());
        }
        if (toPay.isEmpty()) {
            throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                    "The balance does not cover the oldest sanctioned claim. Record an allocation first");
        }
        String ref = "PB/" + school.code() + "/" + fy + "/" + String.format("%05d", batches.nextNumber());
        Instant now = clock.instant();
        batches.save(new PaymentBatch(ref, schoolId, fy, total, toPay.size(), me.userId(), now));
        claims.markPaid(toPay, ref);
        audit.record("PAYMENT_BATCH_CREATED", "SCHOOL", schoolId, ref + " total " + total + " claims " + toPay.size());
        return new BatchView(ref, fy, total, toPay.size(), now);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private SchoolRef schoolOfMyPao(MrmsPrincipal me, Long schoolId) {
        return organisation.school(schoolId).filter(s -> s.paoId().equals(me.paoId()))
                .orElseThrow(() -> new NotFoundException("School"));
    }

    private List<DemandView> demandViews(List<BudgetDemand> list) {
        Map<Long, String> names = accounts.fullNames(list.stream()
                .flatMap(d -> Stream.of(d.getRaisedBy(), d.getAcknowledgedBy())).filter(Objects::nonNull).toList());
        Map<Long, String> schools = organisation.schoolNames(list.stream().map(BudgetDemand::getSchoolId).toList());
        return list.stream().map(d -> new DemandView(d.getId(), d.getSchoolId(), schools.get(d.getSchoolId()),
                d.getFinancialYear(), d.getAmount(), d.getClaimCount(), d.getStatus().name(),
                names.get(d.getRaisedBy()), d.getRaisedAt(),
                d.getAcknowledgedBy() == null ? null : names.get(d.getAcknowledgedBy()), d.getAcknowledgedAt()))
                .toList();
    }

    private List<AllocationView> allocationViews(List<BudgetAllocation> list) {
        Map<Long, String> names = accounts.fullNames(list.stream().map(BudgetAllocation::getAllocatedBy).toList());
        return list.stream().map(a -> new AllocationView(a.getId(), a.getFinancialYear(), a.getAmount(),
                a.getSanctionOrderNo(), a.getRemarks(), names.get(a.getAllocatedBy()), a.getAllocatedAt())).toList();
    }

    private static MrmsPrincipal requireRole(Role role) {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != role) {
            throw new ForbiddenException("This action is only for the " + role.label());
        }
        return me;
    }

    private static MrmsPrincipal requirePao() {
        MrmsPrincipal me = CurrentUser.get();
        if (me.role() != Role.PAO_AUDITOR && me.role() != Role.PAO_OFFICER) {
            throw new ForbiddenException("Only PAO officials can view budgets of schools");
        }
        return me;
    }

    record DemandView(Long id, Long schoolId, String schoolName, String financialYear, BigDecimal amount,
                      int claimCount, String status, String raisedBy, Instant raisedAt, String acknowledgedBy,
                      Instant acknowledgedAt) {
    }

    record AllocationView(Long id, String financialYear, BigDecimal amount, String sanctionOrderNo, String remarks,
                          String allocatedBy, Instant allocatedAt) {
    }

    record BatchView(String batchRef, String financialYear, BigDecimal totalAmount, int claimCount,
                     Instant createdAt) {
    }
}
