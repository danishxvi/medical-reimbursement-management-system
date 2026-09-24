package com.mrms.claim;

import java.math.BigDecimal;
import java.util.Map;

/** Aggregates for the role dashboards. */
public interface ClaimStatistics {

    Snapshot forEmployee(Long employeeUserId);

    Snapshot forSchool(Long schoolId);

    Snapshot forPao(Long paoId);

    Snapshot overall();

    /**
     * @param countsByStatus    number of claims per status name
     * @param claimedTotal      sum claimed over all non draft claims
     * @param paidTotal         sum paid
     * @param overdueByStage    claims past their SLA, keyed by status name
     * @param averageDaysToPay  mean days from first submission to payment (null if none paid)
     */
    record Snapshot(Map<String, Long> countsByStatus, BigDecimal claimedTotal, BigDecimal paidTotal,
                    Map<String, Long> overdueByStage, Double averageDaysToPay) {
    }
}
