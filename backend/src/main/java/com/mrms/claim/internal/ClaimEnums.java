package com.mrms.claim.internal;

/** Enumerations used inside the claim module and exposed through the meta endpoint. */
final class ClaimEnums {

    private ClaimEnums() {
    }

    enum TreatmentType {
        OPD("Out patient (OPD)"),
        INDOOR("Indoor (admitted)");

        final String label;

        TreatmentType(String label) {
            this.label = label;
        }
    }

    enum HospitalType {
        GOVERNMENT("Government"),
        EMPANELLED("Panel (empanelled under DGEHS)"),
        PRIVATE("Private");

        final String label;

        HospitalType(String label) {
            this.label = label;
        }
    }

    /** Columns of the "Total amount claimed" table in Annexure II. */
    enum ItemCategory {
        CONSULTATION("Consultation charges"),
        INVESTIGATION("Investigation charges"),
        MEDICINE("Medical charges (medicines)"),
        OTHER("Other charges");

        final String label;

        ItemCategory(String label) {
            this.label = label;
        }
    }

    enum Recommendation { SANCTION, REJECT }

    /**
     * Standard objection codes. Using a fixed list keeps objections
     * consistent across offices and lets the Directorate see which problems
     * are most common.
     */
    enum ReturnReason {
        BILL_ILLEGIBLE("Bill or receipt is not legible"),
        BILL_MISSING("Bill or receipt is missing for an item"),
        PRESCRIPTION_MISSING("Prescription or discharge summary is missing"),
        NAC_NOT_COVERED("Item is not covered by a non availability certificate"),
        AMOUNT_MISMATCH("Amount entered does not match the bill"),
        PATIENT_DETAILS("Patient or relationship details are incorrect"),
        DATES_INCONSISTENT("Dates are inconsistent with the treatment period"),
        CARD_VALIDITY("DGEHS card validity does not cover the treatment"),
        REFERRAL_MISSING("Referral or authorisation from AMA is missing"),
        DUPLICATE_BILL("Bill appears to be claimed already"),
        DGEHS_RATE("Rates need restriction as per DGEHS"),
        OTHER("Other (see remarks)");

        final String label;

        ReturnReason(String label) {
            this.label = label;
        }
    }
}
