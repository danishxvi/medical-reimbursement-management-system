package com.mrms.identity;

/**
 * Result of creating an account. The temporary password is returned exactly
 * once so the administrator can hand it over; it is never stored in clear.
 */
public record CreatedAccount(Long id, String username, String temporaryPassword) {
}
