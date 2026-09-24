package com.mrms.enac;

/** Enumerations shared by the e-NAC API. */
public final class NacTypes {

    private NacTypes() {
    }

    public enum NacStatus {
        PENDING_PHARMACIST,
        PENDING_MEDICAL_OFFICER,
        ISSUED,
        RETURNED
    }

    public enum ItemType {
        MEDICINE,
        INVESTIGATION,
        OTHER
    }

    public enum Decision {
        /** Given from dispensary stock: not reimbursable. */
        AVAILABLE,
        /** Not in stock: may be bought and claimed. */
        NOT_AVAILABLE,
        /** Not admissible under DGEHS: may not be claimed. */
        NOT_ADMISSIBLE
    }
}
