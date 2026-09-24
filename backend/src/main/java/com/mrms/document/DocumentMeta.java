package com.mrms.document;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a stored document. The random UUID id is not guessable. */
public record DocumentMeta(
        UUID id,
        Long ownerUserId,
        DocumentCategory category,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt) {
}
