package com.mrms.rates;

/**
 * Which column of the rate list applies to a hospital, chosen by the Head
 * of School for a claim. Per the CGHS memorandum of 03.10.2025, treatment in
 * a non empanelled private hospital is restricted to Non-NABH rates.
 */
public enum RateBasis {
    NABH("NABH / NABL accredited hospital"),
    NON_NABH("Non-NABH hospital (also any non empanelled private hospital)"),
    SUPER_SPECIALITY("Super speciality hospital"),
    AS_BILLED("Government hospital: admissible as billed");

    private final String label;

    RateBasis(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
