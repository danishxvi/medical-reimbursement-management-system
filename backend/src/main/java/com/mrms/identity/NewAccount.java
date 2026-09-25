package com.mrms.identity;

import com.mrms.shared.domain.Role;

/**
 * Command to create an account.
 *
 * @param zone            education zone, only for the Zonal Oversight role
 * @param initialPassword if null a random temporary password is generated;
 *                        either way the user must change it at first login
 *                        unless {@code mustChangePassword} is false (demo data only)
 */
public record NewAccount(
        String username,
        String fullName,
        Role role,
        String email,
        String mobile,
        Long schoolId,
        Long dispensaryId,
        Long paoId,
        String zone,
        String initialPassword,
        boolean mustChangePassword) {
}
