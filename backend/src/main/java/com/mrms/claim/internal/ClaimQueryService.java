package com.mrms.claim.internal;

import com.mrms.claim.ClaimPayments;
import com.mrms.claim.ClaimStatistics;
import com.mrms.claim.ClaimStatus;
import com.mrms.identity.Accounts;
import com.mrms.shared.web.BusinessRuleException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implements the claim module's public query and payment APIs. */
@Service
class ClaimQueryService implements ClaimPayments, ClaimStatistics {

    private static final Set<ClaimStatus> SLA_STAGES =
            EnumSet.of(ClaimStatus.PENDING_HOS, ClaimStatus.PENDING_PAO_AUDIT, ClaimStatus.PENDING_SANCTION);

    private final ClaimRepository claims;
    private final ClaimSupport support;
    private final Accounts accounts;
    private final Clock clock;

    ClaimQueryService(ClaimRepository claims, ClaimSupport support, Accounts accounts, Clock clock) {
        this.claims = claims;
        this.support = support;
        this.accounts = accounts;
        this.clock = clock;
    }

    // ==================================================================
    // Payments
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<PayableClaim> sanctionedAwaitingPayment(Long schoolId) {
        List<Claim> list = claims.findBySchoolIdAndStatusOrderByFirstSubmittedAtAscIdAsc(schoolId, ClaimStatus.SANCTIONED);
        Map<Long, String> names = accounts.fullNames(list.stream().map(Claim::getEmployeeUserId).toList());
        return list.stream().map(c -> new PayableClaim(c.getId(), c.getClaimNumber(),
                names.get(c.getEmployeeUserId()), c.getAdmittedAmount(), c.getFirstSubmittedAt(),
                c.getSanctionedAt())).toList();
    }

    @Override
    @Transactional
    public void markPaid(List<Long> claimIds, String batchRef) {
        Instant now = clock.instant();
        List<Claim> list = claims.findAllById(claimIds);
        if (list.size() != claimIds.size()) {
            throw new BusinessRuleException("UNKNOWN_CLAIM", "A claim in the payment batch was not found");
        }
        for (Claim claim : list) {
            ClaimStatus from = claim.markPaid(batchRef, now);
            support.recordTransition(claim, "PAID", from, "Payment batch " + batchRef, null);
        }
        claims.saveAll(list);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolPipeline pipeline(Long schoolId) {
        long pendingCount = 0;
        long sanctionedCount = 0;
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal sanctioned = BigDecimal.ZERO;
        for (Claim c : claims.findBySchoolIdAndStatusIn(schoolId, ClaimStatus.OUTSTANDING)) {
            if (c.getStatus() == ClaimStatus.SANCTIONED) {
                sanctionedCount++;
                sanctioned = sanctioned.add(c.getAdmittedAmount());
            } else {
                pendingCount++;
                // Best estimate of what will be paid: the most scrutinised figure available
                BigDecimal estimate = c.getAdmittedAmount() != null ? c.getAdmittedAmount()
                        : c.getRestrictedAmount() != null ? c.getRestrictedAmount() : c.getClaimedAmount();
                pending = pending.add(estimate);
            }
        }
        return new SchoolPipeline(pendingCount, pending, sanctionedCount, sanctioned);
    }

    // ==================================================================
    // Statistics
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public Snapshot forEmployee(Long employeeUserId) {
        return snapshot(claims.statsForEmployee(employeeUserId), claims.paidTotal(employeeUserId, null, null),
                Map.of(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot forSchool(Long schoolId) {
        return snapshot(claims.statsForSchool(schoolId), claims.paidTotal(null, schoolId, null),
                overdue(schoolId, null), averageDays(claims.recentPaidDurations(schoolId, null, Limit.of(200))));
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot forPao(Long paoId) {
        return snapshot(claims.statsForPao(paoId), claims.paidTotal(null, null, paoId),
                overdue(null, paoId), averageDays(claims.recentPaidDurations(null, paoId, Limit.of(200))));
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot overall() {
        return snapshot(claims.statsOverall(), claims.paidTotal(null, null, null),
                overdue(null, null), averageDays(claims.recentPaidDurations(null, null, Limit.of(500))));
    }

    private Map<String, Long> overdue(Long schoolId, Long paoId) {
        Map<String, Long> result = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (ClaimStatus stage : SLA_STAGES) {
            Instant before = now.minus(Duration.ofDays(support.slaDays(stage)));
            long count = claims.countStageEnteredBefore(Set.of(stage), before, schoolId, paoId).stream()
                    .mapToLong(row -> ((Number) row[1]).longValue()).sum();
            result.put(stage.name(), count);
        }
        return result;
    }

    private static Snapshot snapshot(List<Object[]> rows, BigDecimal paid, Map<String, Long> overdue,
                                     Double averageDays) {
        Map<String, Long> counts = new HashMap<>();
        for (ClaimStatus s : ClaimStatus.values()) {
            counts.put(s.name(), 0L);
        }
        BigDecimal claimed = BigDecimal.ZERO;
        for (Object[] row : rows) {
            ClaimStatus status = (ClaimStatus) row[0];
            counts.put(status.name(), ((Number) row[1]).longValue());
            if (status != ClaimStatus.DRAFT && status != ClaimStatus.WITHDRAWN) {
                claimed = claimed.add(toBigDecimal(row[2]));
            }
        }
        return new Snapshot(counts, claimed, paid == null ? BigDecimal.ZERO : paid, overdue, averageDays);
    }

    private static Double averageDays(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        double total = 0;
        for (Object[] row : rows) {
            total += Duration.between((Instant) row[0], (Instant) row[1]).toHours() / 24.0;
        }
        return Math.round(total / rows.size() * 10) / 10.0;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
