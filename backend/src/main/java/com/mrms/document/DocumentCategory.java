package com.mrms.document;

/**
 * Kinds of supporting documents. The list follows the DGEHS check list
 * (Annexure I) so the check list can be derived from what was uploaded.
 */
public enum DocumentCategory {
    PRESCRIPTION("Prescription (OPD) / discharge summary (indoor)"),
    BILL("Original bill / receipt"),
    DGEHS_CARD("DGEHS card showing validity"),
    REFERRAL("Referral / authorisation from AMA"),
    DISCHARGE_SUMMARY("Discharge summary"),
    LAB_BREAKUP("Break up for lab investigation"),
    DRUG_BREAKUP("Break up for drugs prescribed"),
    INVESTIGATION_REPORT("Investigation report"),
    EMERGENCY_CERTIFICATE("Emergency certificate from hospital"),
    EMERGENCY_LETTER("Self explanatory letter for emergency"),
    NAC_SCAN("Non availability certificate (scanned, legacy)"),
    CANCELLED_CHEQUE("Cancelled cheque"),
    AFFIDAVIT("Affidavit on stamp paper"),
    DEATH_CERTIFICATE("Death certificate"),
    OTHER("Other supporting document");

    private final String label;

    DocumentCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
