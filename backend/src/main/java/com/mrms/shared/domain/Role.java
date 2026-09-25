package com.mrms.shared.domain;

/**
 * Every account has exactly one role. The role decides which dashboard the
 * user sees and which office column (school, dispensary or PAO) scopes the
 * data they can reach.
 */
public enum Role {

    EMPLOYEE("Employee", OfficeScope.SCHOOL),
    HOS("Head of School", OfficeScope.SCHOOL),
    PHARMACIST("Pharmacist", OfficeScope.DISPENSARY),
    MEDICAL_OFFICER("Medical Officer", OfficeScope.DISPENSARY),
    PAO_AUDITOR("PAO Auditor", OfficeScope.PAO),
    PAO_OFFICER("PAO Officer", OfficeScope.PAO),
    ADMIN("Administrator", OfficeScope.NONE),
    /** Deputy Director of Education for a zone: follows up delays, sees status and dates, never documents. */
    OVERSIGHT("Zonal Oversight Officer", OfficeScope.ZONE);

    private final String label;
    private final OfficeScope scope;

    Role(String label, OfficeScope scope) {
        this.label = label;
        this.scope = scope;
    }

    public String label() {
        return label;
    }

    public OfficeScope scope() {
        return scope;
    }

    /** Authority name used by Spring Security, for example ROLE_HOS. */
    public String authority() {
        return "ROLE_" + name();
    }

    public enum OfficeScope { SCHOOL, DISPENSARY, PAO, ZONE, NONE }
}
