package com.mrms.enac;

import com.mrms.enac.NacTypes.NacStatus;

/**
 * Published inside the transaction whenever a request moves to a new
 * status. Listeners (notifications) run in the same transaction.
 */
public record NacStatusChanged(
        Long nacRequestId,
        String nacNumber,
        Long employeeUserId,
        Long dispensaryId,
        Long assignedPharmacistId,
        NacStatus newStatus,
        String remarks) {
}
