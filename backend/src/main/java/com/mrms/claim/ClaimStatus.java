package com.mrms.claim;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    DRAFT("Draft"),
    PENDING_HOS("With Head of School"),
    RETURNED_BY_HOS("Returned by Head of School"),
    PENDING_PAO_AUDIT("With PAO auditor"),
    RETURNED_BY_PAO("Returned by PAO"),
    PENDING_SANCTION("Awaiting sanction"),
    SANCTIONED("Sanctioned, awaiting funds"),
    PAID("Paid"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    /** Claims that are "live": used for duplicate detection and budget demand. */
    public static final Set<ClaimStatus> ACTIVE = EnumSet.of(PENDING_HOS, RETURNED_BY_HOS, PENDING_PAO_AUDIT,
            RETURNED_BY_PAO, PENDING_SANCTION, SANCTIONED, PAID);

    /** Claims waiting for money that is not yet paid out. */
    public static final Set<ClaimStatus> OUTSTANDING = EnumSet.of(PENDING_HOS, RETURNED_BY_HOS, PENDING_PAO_AUDIT,
            RETURNED_BY_PAO, PENDING_SANCTION, SANCTIONED);

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
