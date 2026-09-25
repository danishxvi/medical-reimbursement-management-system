package com.mrms.identity.internal;

import com.mrms.identity.AccountSummary;
import com.mrms.shared.domain.Role;
import com.mrms.shared.security.MrmsPrincipal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "user_account")
class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;

    private String mobile;

    @Column(name = "school_id")
    private Long schoolId;

    @Column(name = "dispensary_id")
    private Long dispensaryId;

    @Column(name = "pao_id")
    private Long paoId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "privacy_notice_version")
    private String privacyNoticeVersion;

    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;

    @Column(name = "guide_seen_at")
    private Instant guideSeenAt;

    @Version
    private long version;

    protected UserAccount() {
    }

    UserAccount(String username, String passwordHash, Role role, String fullName, String email, String mobile,
                Long schoolId, Long dispensaryId, Long paoId, boolean mustChangePassword, Instant now) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
        this.mobile = mobile;
        this.schoolId = schoolId;
        this.dispensaryId = dispensaryId;
        this.paoId = paoId;
        this.enabled = true;
        this.mustChangePassword = mustChangePassword;
        this.passwordChangedAt = now;
        this.createdAt = now;
    }

    boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counts a failed attempt and locks the account once the limit is hit. */
    void registerFailure(int maxAttempts, Duration lockout, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedAttempts = 0;
        }
    }

    void registerSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    void changePassword(String newHash, boolean temporary, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = temporary;
        this.passwordChangedAt = now;
    }

    void unlock() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void updateContact(String email, String mobile) {
        this.email = email;
        this.mobile = mobile;
    }

    void acceptPrivacyNotice(String version, Instant now) {
        this.privacyNoticeVersion = version;
        this.privacyAcceptedAt = now;
    }

    void markGuideSeen(Instant now) {
        this.guideSeenAt = now;
    }

    boolean hasAccepted(String currentNoticeVersion) {
        return currentNoticeVersion.equals(privacyNoticeVersion);
    }

    boolean hasSeenGuide() {
        return guideSeenAt != null;
    }

    MrmsPrincipal toPrincipal() {
        return new MrmsPrincipal(id, username, fullName, role, schoolId, dispensaryId, paoId, mustChangePassword);
    }

    AccountSummary toSummary(Instant now) {
        return new AccountSummary(id, username, fullName, role, email, mobile, schoolId, dispensaryId, paoId,
                enabled, isLocked(now), lastLoginAt);
    }

    Long getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }

    String getFullName() {
        return fullName;
    }

    boolean isEnabled() {
        return enabled;
    }

    boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
