package com.mrms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Typed view of the {@code mrms.*} settings in application.yml.
 */
@ConfigurationProperties(prefix = "mrms")
public record MrmsProperties(
        Cors cors,
        Csrf csrf,
        Storage storage,
        Security security,
        Sla sla,
        Claim claim,
        Bootstrap bootstrap,
        Demo demo,
        Documents documents,
        Antivirus antivirus) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Csrf(boolean secureCookie) {
    }

    public record Storage(String root, String encryptionKey, long maxFileBytes) {
    }

    public record Security(
            int maxFailedLogins,
            int lockoutMinutes,
            int loginRequestsPerMinute,
            int apiRequestsPerMinute,
            int passwordHistory) {
    }

    /** Service level targets in days for each queue. */
    public record Sla(
            int hosDays,
            int paoAuditDays,
            int sanctionDays,
            int pharmacistDays,
            int medicalOfficerDays) {
    }

    public record Claim(int submissionWindowDays, int maxItems) {
    }

    public record Bootstrap(String adminUsername, String adminPassword) {
    }

    public record Demo(boolean seed) {
    }

    /** Standard file name patterns, see DocumentNaming for the tokens. */
    public record Documents(String uploadPattern, String claimPattern) {
    }

    /**
     * Virus scanning of uploads. mode: clamd (default) or disabled, which
     * is only accepted together with allowDisabled (local development).
     * With failClosed an upload is refused while the scanner is unreachable.
     */
    public record Antivirus(String mode, String host, int port, int timeoutMillis, boolean failClosed,
                            boolean allowDisabled) {
    }

    public Documents documents() {
        return documents == null ? new Documents(null, null) : documents;
    }

    public Antivirus antivirus() {
        return antivirus == null ? new Antivirus("clamd", "localhost", 3310, 10_000, true, false) : antivirus;
    }

    public Demo demo() {
        return demo == null ? new Demo(false) : demo;
    }
}
