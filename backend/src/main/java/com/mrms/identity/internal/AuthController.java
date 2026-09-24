package com.mrms.identity.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Login, logout (handled by Spring Security at POST /api/auth/logout),
 * current user and password change.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final String GENERIC_FAILURE = "Invalid Employee ID or password. "
            + "After repeated failures the account is locked for a short time.";

    private final AccountService accounts;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final AuditTrail audit;

    AuthController(AccountService accounts, SessionAuthenticationStrategy sessionStrategy,
                   SecurityContextRepository contextRepository, AuditTrail audit) {
        this.accounts = accounts;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.audit = audit;
    }

    /** Issues the CSRF cookie; the SPA calls this once before logging in. */
    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        MrmsPrincipal principal = accounts.authenticate(body.username(), body.password())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", GENERIC_FAILURE));

        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(principal.role().authority())));

        // Rotate session id, enforce single session, register it, rotate CSRF token
        sessionStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        audit.record("LOGIN_SUCCESS", "USER", principal.userId(), null);
        return MeResponse.of(principal);
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.of(CurrentUser.get());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest body,
                        HttpServletRequest request, HttpServletResponse response) {
        MrmsPrincipal me = CurrentUser.get();
        accounts.changeOwnPassword(body.currentPassword(), body.newPassword());

        // Refresh the principal in the session so the "must change" gate opens
        var current = SecurityContextHolder.getContext().getAuthentication();
        var refreshed = UsernamePasswordAuthenticationToken.authenticated(me.withPasswordChanged(), null,
                current.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        // Any other device signed in with the old password is signed out
        accounts.expireOtherSessions(me.userId(), request.getSession().getId());
    }

    @PutMapping("/contact")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateContact(@Valid @RequestBody ContactRequest body) {
        accounts.updateOwnContact(body.email(), body.mobile());
    }

    record LoginRequest(
            @NotBlank @Size(max = 40) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    record ContactRequest(
            @Email @Size(max = 150) String email,
            @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Enter a 10 digit mobile number") String mobile) {
    }

    record MeResponse(Long id, String username, String fullName, String role, String roleLabel,
                      Long schoolId, Long dispensaryId, Long paoId, boolean mustChangePassword) {

        static MeResponse of(MrmsPrincipal p) {
            return new MeResponse(p.userId(), p.username(), p.fullName(), p.role().name(), p.role().label(),
                    p.schoolId(), p.dispensaryId(), p.paoId(), p.mustChangePassword());
        }
    }
}
