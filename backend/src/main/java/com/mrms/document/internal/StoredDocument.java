package com.mrms.document.internal;

import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Uploaded files never change; a correction is a new upload. */
@Entity
@Immutable
@Table(name = "stored_document")
class StoredDocument {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentCategory category;

    @Column(name = "standard_name", nullable = false)
    private String standardName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "scan_status", nullable = false)
    private String scanStatus;

    @Column(name = "scan_engine")
    private String scanEngine;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    protected StoredDocument() {
    }

    StoredDocument(UUID id, Long ownerUserId, DocumentCategory category, String standardName, String originalName,
                   String contentType, long sizeBytes, String sha256, String storageKey, Instant uploadedAt,
                   VirusScanner.Result scan) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.category = category;
        this.standardName = standardName;
        this.originalName = originalName;
        this.scanStatus = scan.status().name();
        this.scanEngine = scan.engine();
        this.scannedAt = scan.scannedAt();
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedAt = uploadedAt;
    }

    String getStorageKey() {
        return storageKey;
    }

    DocumentMeta toMeta() {
        return new DocumentMeta(id, ownerUserId, category, standardName, originalName, contentType, sizeBytes, sha256,
                uploadedAt, scanStatus);
    }
}
