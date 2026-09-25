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
        Antivirus antivirus,
        Esign esign,
        Jobs jobs,
        Notify notifications) {

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
    /**
     * Time limits in days for each queue, and when the watch acts on them.
     *
     * @param reminderPercent  share of the limit after which the officials are reminded (default 80)
     * @param escalationFactor multiple of the limit after which it goes to the zonal office (default 2)
     */
    public record Sla(
            int hosDays,
            int paoAuditDays,
            int sanctionDays,
            int pharmacistDays,
            int medicalOfficerDays,
            int reminderPercent,
            int escalationFactor) {

        public Sla {
            if (reminderPercent <= 0 || reminderPercent >= 100) {
                reminderPercent = 80;
            }
            if (escalationFactor < 2) {
                escalationFactor = 2;
            }
        }
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

    /**
     * Signing of legally significant actions. mode: password (default) or
     * esign. For esign the Directorate's ASP registration supplies the ASP
     * id, keystore and the ESP's certificates; "simulator" replaces the ESP
     * with a built in one (development and tests only).
     *
     * @param publicBaseUrl        address users reach the portal at, used for the ESP's return URL
     * @param requestField         form field that carries the request XML to the ESP
     * @param responseField        form field that carries the response XML back
     * @param transactionMinutes   how long a started or completed signature stays usable
     */
    public record Esign(String mode, String providerName, String aspId, String espUrl, String publicBaseUrl,
                        String requestField, String responseField, String aspKeystore, String aspKeystorePassword,
                        String aspKeyAlias, String espCertificates, String trustedCas, boolean simulator,
                        int transactionMinutes) {
    }

    public Esign esign() {
        return esign == null ? new Esign("password", null, null, null, null, "eSignRequest", "eSignResponse", null, null,
                null, null, null, false, 10) : esign;
    }

    /** Background jobs (time limit watch, message sending). Off in tests, which run them by hand. */
    public record Jobs(boolean enabled) {
    }

    /**
     * E-mail and SMS delivery. Each channel's mode is off, log (development:
     * written to the log only) or smtp / http.
     *
     * @param portalUrl address of the portal, used in message links
     */
    public record Notify(String portalUrl, Email email, Sms sms) {

        public record Email(String mode, String from) {
        }

        /**
         * @param urlTemplate gateway address with {mobile}, {message} and {templateId} placeholders
         * @param templateId  DLT registered template id (required for SMS in India)
         */
        public record Sms(String mode, String urlTemplate, String templateId) {
        }
    }

    public Jobs jobs() {
        return jobs == null ? new Jobs(true) : jobs;
    }

    public Notify notifications() {
        Notify n = notifications == null ? new Notify(null, null, null) : notifications;
        return new Notify(n.portalUrl() == null ? "" : n.portalUrl(),
                n.email() == null ? new Notify.Email("off", null) : n.email(),
                n.sms() == null ? new Notify.Sms("off", null, null) : n.sms());
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
