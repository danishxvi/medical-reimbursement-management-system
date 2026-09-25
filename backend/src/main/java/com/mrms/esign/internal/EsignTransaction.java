package com.mrms.esign.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** One Aadhaar eSign attempt, from request to the single action it confirms. */
@Entity
@Table(name = "esign_transaction")
class EsignTransaction {

    enum Status { PENDING, SIGNED, FAILED, CONSUMED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String txn;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Column(nullable = false)
    private String digest;

    @Column(name = "document_info", nullable = false)
    private String documentInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signer_name")
    private String signerName;

    @Column(name = "certificate_serial")
    private String certificateSerial;

    @Column(name = "certificate_issuer")
    private String certificateIssuer;

    @Column(name = "signature_pkcs7")
    private String signaturePkcs7;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Version
    private long version;

    protected EsignTransaction() {
    }

    EsignTransaction(String txn, Long userId, String purpose, String subjectType, String subjectId, String digest,
                     String documentInfo, Instant now, Instant expiresAt) {
        this.txn = txn;
        this.userId = userId;
        this.purpose = purpose;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.digest = digest;
        this.documentInfo = documentInfo;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isOpen(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    void signed(String signerName, String serial, String issuer, String pkcs7Base64, Instant now, Instant validUntil) {
        this.status = Status.SIGNED;
        this.signerName = signerName;
        this.certificateSerial = serial;
        this.certificateIssuer = issuer;
        this.signaturePkcs7 = pkcs7Base64;
        this.signedAt = now;
        this.expiresAt = validUntil;
    }

    void failed(String message) {
        this.status = Status.FAILED;
        this.errorMessage = message == null ? "Signing failed"
                : message.length() > 300 ? message.substring(0, 300) : message;
    }

    void consume(Instant now) {
        this.status = Status.CONSUMED;
        this.consumedAt = now;
    }

    String getTxn() { return txn; }
    Long getUserId() { return userId; }
    String getPurpose() { return purpose; }
    String getSubjectType() { return subjectType; }
    String getSubjectId() { return subjectId; }
    String getDigest() { return digest; }
    String getDocumentInfo() { return documentInfo; }
    Status getStatus() { return status; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getSignedAt() { return signedAt; }
    String getSignerName() { return signerName; }
    String getCertificateSerial() { return certificateSerial; }
    String getCertificateIssuer() { return certificateIssuer; }
    String getErrorMessage() { return errorMessage; }
}
