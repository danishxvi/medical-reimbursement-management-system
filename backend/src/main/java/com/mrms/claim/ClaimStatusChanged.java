package com.mrms.claim;

/**
 * Published inside the transaction whenever a claim changes status.
 * Listeners run synchronously in the same transaction.
 */
public record ClaimStatusChanged(
        Long claimId,
        String claimNumber,
        Long employeeUserId,
        Long schoolId,
        Long paoId,
        ClaimStatus from,
        ClaimStatus to,
        Long assignedTo,
        String remarks) {
}
