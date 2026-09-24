package com.mrms.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Accounts created by an administrator start with a temporary password.
 * Until it is changed, only the authentication endpoints are reachable.
 */
final class PasswordChangeEnforcementFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean mustChange = CurrentUser.find().map(MrmsPrincipal::mustChangePassword).orElse(false);
        String uri = request.getRequestURI();
        if (mustChange && uri.startsWith("/api/") && !uri.startsWith("/api/auth/")) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                    + "\"detail\":\"Please change your temporary password to continue\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
