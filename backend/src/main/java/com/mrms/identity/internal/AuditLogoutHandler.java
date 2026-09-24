package com.mrms.identity.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.shared.security.MrmsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/** Writes a LOGOUT entry to the audit trail before the session is destroyed. */
@Component
class AuditLogoutHandler implements LogoutHandler {

    private final AuditTrail audit;

    AuditLogoutHandler(AuditTrail audit) {
        this.audit = audit;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof MrmsPrincipal principal) {
            audit.record("LOGOUT", "USER", principal.userId(), null);
        }
    }
}
