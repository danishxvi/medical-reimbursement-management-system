package com.mrms.shared.security;

import com.mrms.shared.web.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Static access to the logged in user. Services use this instead of taking
 * user ids from request bodies, so a caller can never act as someone else.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<MrmsPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MrmsPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static MrmsPrincipal get() {
        return find().orElseThrow(() -> new ForbiddenException("Not authenticated"));
    }

    public static Long id() {
        return get().userId();
    }
}
