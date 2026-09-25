package com.mrms.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a stored document. The random UUID id is not guessable.
 *
 * @param standardName name given by the system at upload (see DocumentNames)
 * @param originalName the name the user's file had, kept only for reference
 * @param scanStatus   CLEAN or NOT_SCANNED (infected files are never stored)
 */
public record DocumentMeta(
        UUID id,
        Long ownerUserId,
        DocumentCategory category,
        String standardName,
        String originalName,
        String contentType,
        long sizeBytes,
        String sha256,
        Instant uploadedAt,
        String scanStatus) {
}
