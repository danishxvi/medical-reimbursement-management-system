package com.mrms.document.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.web.BusinessRuleException;
import com.mrms.shared.web.NotFoundException;
import com.mrms.shared.web.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    List<StoredDocument> findBySha256(String sha256);

    long countByOwnerUserIdAndCategoryAndUploadedAtGreaterThanEqual(Long ownerUserId, DocumentCategory category,
                                                                  Instant since);
}

@Service
class DocumentService implements DocumentStore {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Standard names are dated in Indian Standard Time. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final StoredDocumentRepository repository;
    private final EncryptedFileStorage storage;
    private final VirusScanner scanner;
    private final DocumentNaming naming;
    private final AuditTrail audit;
    private final MrmsProperties props;
    private final Clock clock;

    DocumentService(StoredDocumentRepository repository, EncryptedFileStorage storage, VirusScanner scanner,
                    DocumentNaming naming, AuditTrail audit, MrmsProperties props, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.scanner = scanner;
        this.naming = naming;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Order of checks: type and size from the content, then the virus scan,
     * and only then a standard name, encryption and storage. A refused file
     * is never written anywhere.
     */
    @Override
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public DocumentMeta store(Long ownerUserId, String ownerCode, DocumentCategory category, String originalName,
                              byte[] content) {
        FileInspector.FileType type = FileInspector.inspect(originalName, content, props.storage().maxFileBytes());
        VirusScanner.Result scan = scan(content);
        if (scan.status() == VirusScanner.Status.INFECTED) {
            // Recorded even though the upload fails (no rollback for this exception)
            audit.record("UPLOAD_REJECTED_MALWARE", "USER", ownerUserId,
                    category + ", " + content.length + " bytes, " + scan.signature());
            throw new BusinessRuleException("MALWARE_DETECTED", "This file contains a virus or malicious content ("
                    + scan.signature() + ") and was not accepted. Please scan your device and upload a clean copy");
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, IST);
        long earlierToday = repository.countByOwnerUserIdAndCategoryAndUploadedAtGreaterThanEqual(ownerUserId,
                category, today.atStartOfDay(IST).toInstant());
        String standardName = naming.forUpload(ownerCode, category, today, (int) earlierToday + 1, type.contentType);

        UUID id = UUID.randomUUID();
        String storageKey = UUID.randomUUID().toString().replace("-", "");

        storage.write(storageKey, content);
        // If the database insert rolls back, remove the orphaned file
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        storage.deleteQuietly(storageKey);
                    }
                }
            });
        }

        StoredDocument doc = repository.save(new StoredDocument(id, ownerUserId, category, standardName,
                FileInspector.safeName(originalName), type.contentType, content.length, sha256(content),
                storageKey, now, scan));
        audit.record("DOCUMENT_UPLOADED", "DOCUMENT", id,
                standardName + ", " + content.length + " bytes, scan " + scan.status());
        return doc.toMeta();
    }

    @Override
    public String claimFileName(String claimNumber, DocumentCategory category, int seq, String contentType) {
        return naming.forClaim(claimNumber, category, seq, contentType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentMeta> meta(UUID id) {
        return repository.findById(id).map(StoredDocument::toMeta);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMeta> metas(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(StoredDocument::toMeta).toList();
    }

    @Override
    @Transactional
    public DocumentContent read(UUID id) {
        StoredDocument doc = repository.findById(id).orElseThrow(() -> new NotFoundException("Document"));
        byte[] bytes = storage.read(doc.getStorageKey());
        audit.record("DOCUMENT_VIEWED", "DOCUMENT", id, null);
        return new DocumentContent(doc.toMeta(), bytes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMeta> sameContent(String sha256) {
        return repository.findBySha256(sha256).stream().map(StoredDocument::toMeta).toList();
    }

    private VirusScanner.Result scan(byte[] content) {
        try {
            return scanner.scan(content);
        } catch (VirusScanner.UnavailableException e) {
            if (props.antivirus().failClosed()) {
                log.error("Upload refused: {}", e.getMessage());
                throw new ServiceUnavailableException("SCANNER_UNAVAILABLE",
                        "Uploads are paused because virus scanning is not available. Please try again in a few minutes");
            }
            log.warn("Virus scan skipped ({}); fail-closed is off", e.getMessage());
            return new VirusScanner.Result(VirusScanner.Status.NOT_SCANNED, null, null, null);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
