package com.mrms.document.internal;

import com.mrms.audit.AuditTrail;
import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.web.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    List<StoredDocument> findBySha256(String sha256);
}

@Service
class DocumentService implements DocumentStore {

    private final StoredDocumentRepository repository;
    private final EncryptedFileStorage storage;
    private final AuditTrail audit;
    private final MrmsProperties props;
    private final Clock clock;

    DocumentService(StoredDocumentRepository repository, EncryptedFileStorage storage, AuditTrail audit,
                    MrmsProperties props, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DocumentMeta store(Long ownerUserId, DocumentCategory category, String originalName, byte[] content) {
        FileInspector.FileType type = FileInspector.inspect(originalName, content, props.storage().maxFileBytes());
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

        StoredDocument doc = repository.save(new StoredDocument(id, ownerUserId, category,
                FileInspector.safeName(originalName), type.contentType, content.length, sha256(content),
                storageKey, clock.instant()));
        audit.record("DOCUMENT_UPLOADED", "DOCUMENT", id, category + ", " + content.length + " bytes");
        return doc.toMeta();
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

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
