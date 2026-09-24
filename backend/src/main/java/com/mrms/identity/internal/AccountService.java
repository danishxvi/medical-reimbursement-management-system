package com.mrms.identity.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.identity.AccountSummary;
import com.mrms.identity.Accounts;
import com.mrms.identity.CreatedAccount;
import com.mrms.identity.NewAccount;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
class AccountService implements Accounts {

    private final UserAccountRepository accounts;
    private final PasswordHistoryRepository history;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final AuditTrail audit;
    private final SessionRegistry sessionRegistry;
    private final MrmsProperties props;
    private final Clock clock;

    /**
     * Hash of a random string. Checked when the username does not exist so
     * that response time does not reveal whether an Employee ID is valid.
     */
    private final String dummyHash;

    AccountService(UserAccountRepository accounts, PasswordHistoryRepository history, PasswordEncoder encoder,
                   PasswordPolicy policy, AuditTrail audit, SessionRegistry sessionRegistry,
                   MrmsProperties props, Clock clock) {
        this.accounts = accounts;
        this.history = history;
        this.encoder = encoder;
        this.policy = policy;
        this.audit = audit;
        this.sessionRegistry = sessionRegistry;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode(policy.generateTemporary());
    }

    // ------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------

    /**
     * Verifies credentials. Returns empty on any failure; the reason is only
     * written to the audit trail, never returned to the caller.
     */
    @Transactional
    public Optional<MrmsPrincipal> authenticate(String rawUsername, String rawPassword) {
        String username = normalise(rawUsername);
        Instant now = clock.instant();
        Optional<UserAccount> found = accounts.findByUsernameIgnoreCase(username);
        if (found.isEmpty()) {
            encoder.matches(rawPassword, dummyHash);
            audit.recordAnonymous(username, "LOGIN_FAILED", "Unknown username");
            return Optional.empty();
        }
        UserAccount account = found.get();
        if (!account.isEnabled()) {
            encoder.matches(rawPassword, dummyHash);
            audit.recordAnonymous(username, "LOGIN_FAILED", "Account disabled");
            return Optional.empty();
        }
        if (account.isLocked(now)) {
            encoder.matches(rawPassword, dummyHash);
            audit.recordAnonymous(username, "LOGIN_BLOCKED", "Account locked");
            return Optional.empty();
        }
        if (!encoder.matches(rawPassword, account.getPasswordHash())) {
            account.registerFailure(props.security().maxFailedLogins(),
                    Duration.ofMinutes(props.security().lockoutMinutes()), now);
            audit.recordAnonymous(username, "LOGIN_FAILED",
                    account.isLocked(now) ? "Wrong password, account locked" : "Wrong password");
            return Optional.empty();
        }
        if (encoder.upgradeEncoding(account.getPasswordHash())) {
            account.changePassword(encoder.encode(rawPassword), account.isMustChangePassword(), now);
        }
        account.registerSuccess(now);
        return Optional.of(account.toPrincipal());
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public void changeOwnPassword(String currentPassword, String newPassword) {
        MrmsPrincipal me = CurrentUser.get();
        UserAccount account = accounts.findById(me.userId()).orElseThrow(() -> new NotFoundException("Account"));
        Instant now = clock.instant();
        if (!encoder.matches(currentPassword, account.getPasswordHash())) {
            account.registerFailure(props.security().maxFailedLogins(),
                    Duration.ofMinutes(props.security().lockoutMinutes()), now);
            audit.record("PASSWORD_CHANGE_FAILED", "USER", account.getId(), "Wrong current password");
            throw new BusinessRuleException("WRONG_PASSWORD", "The current password is not correct");
        }
        setNewPassword(account, newPassword, false, now);
        audit.record("PASSWORD_CHANGED", "USER", account.getId(), null);
    }

    private void setNewPassword(UserAccount account, String newPassword, boolean temporary, Instant now) {
        List<String> problems = policy.check(newPassword, account.getUsername());
        if (!problems.isEmpty()) {
            throw new BusinessRuleException("WEAK_PASSWORD", String.join(". ", problems));
        }
        if (!temporary) {
            boolean reused = encoder.matches(newPassword, account.getPasswordHash())
                    || history.findByUserIdOrderByCreatedAtDesc(account.getId(),
                                    PageRequest.of(0, props.security().passwordHistory()))
                            .stream().anyMatch(h -> encoder.matches(newPassword, h.getPasswordHash()));
            if (reused) {
                throw new BusinessRuleException("PASSWORD_REUSED",
                        "Choose a password you have not used in your last "
                                + props.security().passwordHistory() + " changes");
            }
        }
        history.save(new PasswordHistoryEntry(account.getId(), account.getPasswordHash(), now));
        account.changePassword(encoder.encode(newPassword), temporary, now);
    }

    // ------------------------------------------------------------------
    // Accounts API used by other modules
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public CreatedAccount create(NewAccount cmd) {
        String username = normalise(cmd.username());
        if (!username.matches("[A-Z0-9._-]{3,40}")) {
            throw new BusinessRuleException("INVALID_USERNAME",
                    "Login ID may contain letters, digits, dot, underscore and hyphen (3 to 40 characters)");
        }
        if (accounts.existsByUsernameIgnoreCase(username)) {
            throw new BusinessRuleException("DUPLICATE_USERNAME", "An account with this ID already exists");
        }
        validateScope(cmd);
        String password = cmd.initialPassword() != null ? cmd.initialPassword() : policy.generateTemporary();
        UserAccount account = new UserAccount(username, encoder.encode(password), cmd.role(), cmd.fullName().trim(),
                blankToNull(cmd.email()), blankToNull(cmd.mobile()),
                cmd.schoolId(), cmd.dispensaryId(), cmd.paoId(), cmd.mustChangePassword(), clock.instant());
        accounts.save(account);
        audit.record("ACCOUNT_CREATED", "USER", account.getId(), "Role " + cmd.role() + ", login " + username);
        return new CreatedAccount(account.getId(), username, cmd.initialPassword() == null ? password : null);
    }

    /** Each role must be tied to exactly the office its scope requires. */
    private static void validateScope(NewAccount cmd) {
        boolean ok = switch (cmd.role().scope()) {
            case SCHOOL -> cmd.schoolId() != null && cmd.dispensaryId() == null && cmd.paoId() == null;
            case DISPENSARY -> cmd.dispensaryId() != null && cmd.schoolId() == null && cmd.paoId() == null;
            case PAO -> cmd.paoId() != null && cmd.schoolId() == null && cmd.dispensaryId() == null;
            case NONE -> cmd.schoolId() == null && cmd.dispensaryId() == null && cmd.paoId() == null;
        };
        if (!ok) {
            throw new BusinessRuleException("INVALID_OFFICE",
                    "Select the " + cmd.role().scope().name().toLowerCase(Locale.ROOT) + " for a " + cmd.role().label());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountSummary> find(Long userId) {
        return accounts.findById(userId).map(a -> a.toSummary(clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> fullNames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllById(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> activeUserIds(Role role, Long officeId) {
        return switch (role.scope()) {
            case SCHOOL -> accounts.findActiveIdsInSchool(role, officeId);
            case DISPENSARY -> accounts.findActiveIdsInDispensary(role, officeId);
            case PAO -> accounts.findActiveIdsInPao(role, officeId);
            case NONE -> accounts.findActiveIds(role);
        };
    }

    /**
     * Runs in its own transaction so a failed attempt is counted even though
     * the caller's transaction rolls back when the exception propagates.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessRuleException.class)
    public void confirmPassword(String rawPassword) {
        MrmsPrincipal me = CurrentUser.get();
        UserAccount account = accounts.findById(me.userId()).orElseThrow(() -> new NotFoundException("Account"));
        if (rawPassword == null || !encoder.matches(rawPassword, account.getPasswordHash())) {
            account.registerFailure(props.security().maxFailedLogins(),
                    Duration.ofMinutes(props.security().lockoutMinutes()), clock.instant());
            audit.record("STEP_UP_FAILED", "USER", account.getId(), "Wrong password on confirmation");
            if (account.isLocked(clock.instant())) {
                expireSessions(account.getId());
            }
            throw new BusinessRuleException("PASSWORD_CONFIRMATION_FAILED",
                    "Password confirmation failed. Repeated failures will lock the account");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive(Role role) {
        return accounts.countByRoleAndEnabledTrue(role);
    }

    // ------------------------------------------------------------------
    // Administrator operations
    // ------------------------------------------------------------------

    @Transactional
    public CreatedAccount resetPassword(Long userId) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new NotFoundException("Account"));
        String temporary = policy.generateTemporary();
        setNewPassword(account, temporary, true, clock.instant());
        account.unlock();
        expireSessions(userId);
        audit.record("PASSWORD_RESET", "USER", userId, "Temporary password issued by administrator");
        return new CreatedAccount(userId, account.getUsername(), temporary);
    }

    @Transactional
    public void unlock(Long userId) {
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new NotFoundException("Account"));
        account.unlock();
        audit.record("ACCOUNT_UNLOCKED", "USER", userId, null);
    }

    @Transactional
    public void setEnabled(Long userId, boolean enabled) {
        if (userId.equals(CurrentUser.id())) {
            throw new BusinessRuleException("SELF_DISABLE", "You cannot disable your own account");
        }
        UserAccount account = accounts.findById(userId).orElseThrow(() -> new NotFoundException("Account"));
        account.setEnabled(enabled);
        if (!enabled) {
            expireSessions(userId);
        }
        audit.record(enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "USER", userId, null);
    }

    @Transactional(readOnly = true)
    public Page<AccountSummary> search(Role role, String q, Pageable page) {
        Instant now = clock.instant();
        return accounts.search(role, blankToNull(q), page).map(a -> a.toSummary(now));
    }

    @Transactional
    public void updateOwnContact(String email, String mobile) {
        UserAccount account = accounts.findById(CurrentUser.id()).orElseThrow(() -> new NotFoundException("Account"));
        account.updateContact(blankToNull(email), blankToNull(mobile));
        audit.record("CONTACT_UPDATED", "USER", account.getId(), null);
    }

    boolean adminExists() {
        return accounts.existsByRole(Role.ADMIN);
    }

    /** Ends every live session of the account (used after reset, disable, lockout). */
    void expireSessions(Long userId) {
        sessionRegistry.getAllSessions(MrmsPrincipal.key(userId), false)
                .forEach(SessionInformation::expireNow);
    }

    /** Ends the account's other sessions, keeping the current one. */
    void expireOtherSessions(Long userId, String currentSessionId) {
        sessionRegistry.getAllSessions(MrmsPrincipal.key(userId), false).stream()
                .filter(s -> !s.getSessionId().equals(currentSessionId))
                .forEach(SessionInformation::expireNow);
    }

    private static String normalise(String username) {
        return username == null ? "" : username.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
