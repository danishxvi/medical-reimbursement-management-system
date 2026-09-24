package com.mrms.document;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the document module. */
public interface DocumentStore {

    /**
     * Validates (type by content, size, active PDF content), encrypts and
     * stores a file for its owner.
     */
    DocumentMeta store(Long ownerUserId, DocumentCategory category, String originalName, byte[] content);

    Optional<DocumentMeta> meta(UUID id);

    List<DocumentMeta> metas(Collection<UUID> ids);

    /** Returns decrypted content. Callers must have checked access first. */
    DocumentContent read(UUID id);

    /** Other documents with identical content, used for duplicate bill detection. */
    List<DocumentMeta> sameContent(String sha256);

    record DocumentContent(DocumentMeta meta, byte[] bytes) {

        /**
         * HTTP response that forces a download with the stored content type.
         * Together with the nosniff header this stops a browser from ever
         * executing an uploaded file.
         */
        public ResponseEntity<byte[]> toResponse() {
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(meta.originalName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header("Content-Security-Policy", "default-src 'none'; sandbox")
                    .contentType(MediaType.parseMediaType(meta.contentType()))
                    .contentLength(bytes.length)
                    .body(bytes);
        }
    }
}
