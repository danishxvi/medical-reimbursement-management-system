package com.mrms.esign.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.esign.SignableAction;
import com.mrms.esign.Signatures;
import com.mrms.identity.Accounts;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.security.MrmsPrincipal;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

interface EsignTransactionRepository extends JpaRepository<EsignTransaction, Long> {

    Optional<EsignTransaction> findByTxn(String txn);

    List<EsignTransaction> findBySubjectTypeAndSubjectIdAndStatusOrderBySignedAtAsc(
            String subjectType, String subjectId, EsignTransaction.Status status);
}

@Service
class EsignService implements Signatures {

    private static final Logger log = LoggerFactory.getLogger(EsignService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EsignTransactionRepository transactions;
    private final ObjectProvider<SignableAction> actions;
    private final ObjectProvider<EsignKeys> keys;
    private final Accounts accounts;
    private final AuditTrail audit;
    private final MrmsProperties props;
    private final Clock clock;

    EsignService(EsignTransactionRepository transactions, ObjectProvider<SignableAction> actions,
                 ObjectProvider<EsignKeys> keys, Accounts accounts, AuditTrail audit, MrmsProperties props,
                 Clock clock) {
        this.transactions = transactions;
        this.actions = actions;
        this.keys = keys;
        this.accounts = accounts;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public SigningMode mode() {
        return "esign".equalsIgnoreCase(props.esign().mode()) ? SigningMode.ESIGN : SigningMode.PASSWORD;
    }

    // ------------------------------------------------------------------
    // Confirmation of an action (both modes)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public Evidence confirm(StepUp stepUp, String purpose, String subjectType, String subjectId, String digest) {
        MrmsPrincipal me = CurrentUser.get();
        Instant now = clock.instant();
        if (mode() == SigningMode.PASSWORD) {
            if (stepUp == null || stepUp.password() == null || stepUp.password().isBlank()) {
                throw new BusinessRuleException("PASSWORD_REQUIRED", "Enter your password to sign");
            }
            accounts.confirmPassword(stepUp.password());
            return new Evidence(purpose, "Password confirmation", me.fullName(), null, null, digest, now);
        }
        if (stepUp == null || stepUp.esignTxn() == null || stepUp.esignTxn().isBlank()) {
            throw new BusinessRuleException("ESIGN_REQUIRED", "Sign with Aadhaar eSign to continue");
        }
        EsignTransaction t = transactions.findByTxn(stepUp.esignTxn())
                .filter(x -> x.getUserId().equals(me.userId()))
                .orElseThrow(() -> new BusinessRuleException("ESIGN_INVALID", "The eSign transaction was not found"));
        if (t.getStatus() != EsignTransaction.Status.SIGNED) {
            throw new BusinessRuleException("ESIGN_INVALID", t.getStatus() == EsignTransaction.Status.CONSUMED
                    ? "This signature has already been used. Please sign again"
                    : "The document has not been signed");
        }
        if (!now.isBefore(t.getExpiresAt())) {
            throw new BusinessRuleException("ESIGN_EXPIRED", "The signature has expired. Please sign again");
        }
        if (!t.getPurpose().equals(purpose) || !t.getSubjectType().equals(subjectType)
                || !t.getSubjectId().equals(subjectId)) {
            throw new BusinessRuleException("ESIGN_MISMATCH", "The signature was made for a different action");
        }
        if (!t.getDigest().equals(digest)) {
            audit.record("ESIGN_CONTENT_MISMATCH", subjectType, subjectId, purpose + ", txn " + t.getTxn());
            throw new BusinessRuleException("ESIGN_MISMATCH",
                    "The content changed after it was signed. Please review and sign again");
        }
        t.consume(now);
        audit.record("ESIGN_USED", subjectType, subjectId,
                purpose + ", signer " + t.getSignerName() + ", certificate " + t.getCertificateSerial());
        return evidence(t);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Evidence> forSubject(String subjectType, String subjectId) {
        return transactions.findBySubjectTypeAndSubjectIdAndStatusOrderBySignedAtAsc(subjectType, subjectId,
                EsignTransaction.Status.CONSUMED).stream().map(EsignService::evidence).toList();
    }

    private static Evidence evidence(EsignTransaction t) {
        return new Evidence(t.getPurpose(), "Aadhaar eSign", t.getSignerName(), t.getCertificateSerial(),
                t.getCertificateIssuer(), t.getDigest(), t.getSignedAt());
    }

    // ------------------------------------------------------------------
    // eSign transaction: start, response, status
    // ------------------------------------------------------------------

    record Started(String txn, String espUrl, Map<String, String> fields, String documentInfo, String providerName) {
    }

    @Transactional
    Started start(String purpose, String subjectId, JsonNode payload) {
        if (mode() != SigningMode.ESIGN) {
            throw new BusinessRuleException("ESIGN_DISABLED", "Aadhaar eSign is not enabled on this portal");
        }
        SignableAction action = actions.orderedStream().filter(a -> a.purpose().equals(purpose)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_PURPOSE", "Unknown signing purpose"));
        // The owning module checks permission and computes the digest of the content
        SignableAction.Signable signable = action.describe(subjectId, payload);
        MrmsProperties.Esign cfg = props.esign();
        Instant now = clock.instant();
        String txn = "MRMS-" + HexFormat.of().formatHex(randomBytes(12)).toUpperCase(Locale.ROOT);
        String info = signable.documentInfo().length() > 300 ? signable.documentInfo().substring(0, 300)
                : signable.documentInfo();
        transactions.save(new EsignTransaction(txn, CurrentUser.id(), purpose, action.subjectType(), subjectId,
                signable.digest(), info, now, now.plus(Duration.ofMinutes(cfg.transactionMinutes()))));
        EsignKeys k = keys.getObject();
        String xml = EsignProtocol.signedRequest(txn, cfg.aspId(), responseUrl(), signable.digest(), info, now,
                k.aspKey, k.aspCertificate);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(cfg.requestField(), xml);
        audit.record("ESIGN_STARTED", action.subjectType(), subjectId, purpose + ", txn " + txn);
        return new Started(txn, cfg.espUrl(), fields, info, cfg.providerName());
    }

    /**
     * Handles the ESP's response. Everything is verified before anything is
     * trusted: the ESP's XML signature, the transaction, the status, the
     * PKCS#7 signature over our own digest and the certificate chain.
     *
     * @return the transaction id (when it could be read) and the outcome
     */
    @Transactional
    Outcome complete(String responseXml) {
        Instant now = clock.instant();
        EsignKeys k = keys.getObject();
        EsignProtocol.Response response;
        try {
            response = EsignProtocol.verifiedResponse(responseXml, k.espCertificates);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected eSign response: {}", e.getMessage());
            audit.recordAnonymous(null, "ESIGN_RESPONSE_REJECTED", e.getMessage());
            return new Outcome(null, false, "The response from the eSign provider could not be verified");
        }
        EsignTransaction t = transactions.findByTxn(response.txn()).orElse(null);
        if (t == null || !t.isOpen(now)) {
            return new Outcome(response.txn(), false, t == null ? "Unknown transaction"
                    : "This signing request has expired or was already completed");
        }
        if (!response.success()) {
            t.failed(response.errorMessage() == null || response.errorMessage().isBlank()
                    ? "Signing was not completed (" + response.errorCode() + ")" : response.errorMessage());
            audit.record("ESIGN_FAILED", t.getSubjectType(), t.getSubjectId(), t.getPurpose() + ", "
                    + response.errorCode());
            return new Outcome(t.getTxn(), false, t.getErrorMessage());
        }
        try {
            byte[] pkcs7 = Base64.getMimeDecoder().decode(response.pkcs7());
            EsignProtocol.Signer signer = EsignProtocol.verifyDocumentSignature(pkcs7,
                    HexFormat.of().parseHex(t.getDigest()), k.trustedCas, now);
            t.signed(signer.name(), signer.serial(), signer.issuer(), Base64.getEncoder().encodeToString(pkcs7), now,
                    now.plus(Duration.ofMinutes(props.esign().transactionMinutes())));
            audit.record("ESIGN_SIGNED", t.getSubjectType(), t.getSubjectId(),
                    t.getPurpose() + ", signer " + signer.name() + ", certificate " + signer.serial());
            return new Outcome(t.getTxn(), true, null);
        } catch (IllegalArgumentException e) {
            t.failed("The signature could not be verified");
            audit.record("ESIGN_FAILED", t.getSubjectType(), t.getSubjectId(), t.getPurpose() + ", " + e.getMessage());
            return new Outcome(t.getTxn(), false, "The signature could not be verified");
        }
    }

    record Outcome(String txn, boolean signed, String message) {
    }

    /**
     * The signed request for an open transaction of the current user, so the
     * portal's own launch page can hand it to the ESP (a form POST).
     */
    @Transactional(readOnly = true)
    Started request(String txn) {
        EsignTransaction t = transactions.findByTxn(txn)
                .filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Signing request"));
        if (!t.isOpen(clock.instant())) {
            throw new BusinessRuleException("ESIGN_CLOSED", "This signing request has expired or is complete");
        }
        MrmsProperties.Esign cfg = props.esign();
        EsignKeys k = keys.getObject();
        String xml = EsignProtocol.signedRequest(t.getTxn(), cfg.aspId(), responseUrl(), t.getDigest(),
                t.getDocumentInfo(), clock.instant(), k.aspKey, k.aspCertificate);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(cfg.requestField(), xml);
        return new Started(t.getTxn(), cfg.espUrl(), fields, t.getDocumentInfo(), cfg.providerName());
    }

    record Status(String txn, String status, String signerName, String message) {
    }

    @Transactional(readOnly = true)
    Status status(String txn) {
        EsignTransaction t = transactions.findByTxn(txn)
                .filter(x -> x.getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Signing request"));
        return new Status(t.getTxn(), t.getStatus().name(), t.getSignerName(), t.getErrorMessage());
    }

    String responseUrl() {
        return trimSlash(props.esign().publicBaseUrl()) + "/api/esign/callback";
    }

    String completionUrl(Outcome outcome) {
        return trimSlash(props.esign().publicBaseUrl()) + "/esign/complete?status=" + (outcome.signed() ? "ok" : "failed")
                + (outcome.txn() == null ? "" : "&txn=" + outcome.txn());
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }
}
