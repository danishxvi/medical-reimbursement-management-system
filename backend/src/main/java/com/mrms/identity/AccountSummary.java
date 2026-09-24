package com.mrms.identity;

import com.mrms.shared.domain.Role;

import java.time.Instant;

/** Read model of an account that other modules may use. Never contains the hash. */
public record AccountSummary(
        Long id,
        String username,
        String fullName,
        Role role,
        String email,
        String mobile,
        Long schoolId,
        Long dispensaryId,
        Long paoId,
        boolean enabled,
        boolean locked,
        Instant lastLoginAt) {
}
