package com.mrms.enac;

import com.mrms.enac.NacTypes.Decision;
import com.mrms.enac.NacTypes.ItemType;
import com.mrms.enac.NacTypes.NacStatus;
import com.mrms.shared.domain.Relation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One prescribed item together with the certificate it belongs to. */
public record NacItemRef(
        Long itemId,
        Long nacRequestId,
        String nacNumber,
        NacStatus status,
        Long employeeUserId,
        String patientName,
        Relation patientRelation,
        Long dependentId,
        String itemName,
        ItemType itemType,
        String quantity,
        Decision decision,
        String decisionReason,
        Long dispensaryId,
        UUID prescriptionDocumentId,
        LocalDate prescriptionDate,
        String prescribedBy,
        Instant issuedAt) {

    /** True when the item may be bought from the market and claimed. */
    public boolean claimable() {
        return status == NacStatus.ISSUED && decision == Decision.NOT_AVAILABLE;
    }
}
