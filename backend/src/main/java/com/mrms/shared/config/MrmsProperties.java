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
        Demo demo) {

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

    public Demo demo() {
        return demo == null ? new Demo(false) : demo;
    }
}
