package com.mrms.claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** API used by the budget module to pay sanctioned claims and size demands. */
public interface ClaimPayments {

    /** Sanctioned, unpaid claims of a school in seniority order (oldest first). */
    List<PayableClaim> sanctionedAwaitingPayment(Long schoolId);

    /** Marks the given sanctioned claims as paid under one payment batch. */
    void markPaid(List<Long> claimIds, String batchRef);

    /** Money in the pipeline for a school, used for the automatic budget demand. */
    SchoolPipeline pipeline(Long schoolId);

    record PayableClaim(Long claimId, String claimNumber, String employeeName, BigDecimal admittedAmount,
                        Instant firstSubmittedAt, Instant sanctionedAt) {
    }

    /**
     * @param pendingAmount     claimed (or restricted) amount of claims still under scrutiny
     * @param sanctionedAmount  admitted amount of sanctioned but unpaid claims
     */
    record SchoolPipeline(long pendingCount, BigDecimal pendingAmount, long sanctionedCount,
                          BigDecimal sanctionedAmount) {
    }
}
