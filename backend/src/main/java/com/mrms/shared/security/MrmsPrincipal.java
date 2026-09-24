package com.mrms.shared.security;

import com.mrms.shared.domain.Role;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

/**
 * The authenticated user as stored in the HTTP session. It carries only
 * identifiers and the office scope, never the password hash.
 *
 * @param userId             account id
 * @param username           employee id or official login id
 * @param fullName           display name
 * @param role               the single role of the account
 * @param schoolId           set for EMPLOYEE and HOS
 * @param dispensaryId       set for PHARMACIST and MEDICAL_OFFICER
 * @param paoId              set for PAO_AUDITOR and PAO_OFFICER
 * @param mustChangePassword true until the first password change
 */
public record MrmsPrincipal(
        Long userId,
        String username,
        String fullName,
        Role role,
        Long schoolId,
        Long dispensaryId,
        Long paoId,
        boolean mustChangePassword) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return username;
    }

    public boolean hasRole(Role other) {
        return role == other;
    }

    public MrmsPrincipal withPasswordChanged() {
        return new MrmsPrincipal(userId, username, fullName, role, schoolId, dispensaryId, paoId, false);
    }

    /**
     * Identity is the account id only. The session registry keys sessions
     * by principal, so a principal must stay "equal" after, for example,
     * the password change flag flips.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MrmsPrincipal p && userId != null && userId.equals(p.userId);
    }

    @Override
    public int hashCode() {
        return userId == null ? 0 : userId.hashCode();
    }

    /** Lookup key for the session registry. */
    public static MrmsPrincipal key(Long userId) {
        return new MrmsPrincipal(userId, null, null, null, null, null, null, false);
    }
}
