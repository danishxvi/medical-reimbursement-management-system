package com.mrms.esign;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the signing module. Every legally significant action
 * (certifying, countersigning, sanctioning, rejecting, releasing payment)
 * is confirmed through {@link #confirm}, which works in one of two modes:
 *
 * <ul>
 *   <li>PASSWORD: the signer re-enters their password (step up);</li>
 *   <li>ESIGN: the signer signs with Aadhaar eSign at a licensed eSign
 *       Service Provider. The signature covers a digest of exactly the
 *       content being approved, which the action recomputes before it
 *       executes, so what was signed is what happens.</li>
 * </ul>
 */
public interface Signatures {

    SigningMode mode();

    /**
     * Confirms a signed action and returns the evidence to record with it.
     * In ESIGN mode the transaction is consumed: it can confirm one action only.
     *
     * @param digest the action's own digest of the content being approved,
     *               recomputed from the request that is about to execute
     * @throws com.mrms.shared.web.BusinessRuleException if the step up is missing, wrong, expired or
     *         for different content
     */
    Evidence confirm(StepUp stepUp, String purpose, String subjectType, String subjectId, String digest);

    /** Signatures recorded for a subject, oldest first (eSign only; password confirmations are in the timeline). */
    List<Evidence> forSubject(String subjectType, String subjectId);

    enum SigningMode { PASSWORD, ESIGN }

    /** What the signer supplies: a password, or a completed eSign transaction id. */
    record StepUp(String password, String esignTxn) {

        public static StepUp password(String password) {
            return new StepUp(password, null);
        }
    }

    /**
     * @param method            "Password confirmation" or "Aadhaar eSign"
     * @param signerName        name on the signing certificate (eSign) or account name
     * @param certificateSerial serial number of the signer's certificate (eSign only)
     * @param certificateIssuer issuing CA (eSign only)
     */
    record Evidence(String purpose, String method, String signerName, String certificateSerial,
                    String certificateIssuer, String digest, Instant signedAt) {
    }
}
