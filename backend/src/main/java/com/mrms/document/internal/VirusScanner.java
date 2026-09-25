package com.mrms.document.internal;

import java.time.Instant;

/**
 * Scans an upload before it is stored. Implementations: {@link ClamdScanner}
 * (production) and a disabled scanner for local development.
 */
interface VirusScanner {

    Result scan(byte[] content);

    enum Status { CLEAN, INFECTED, NOT_SCANNED }

    /**
     * @param signature name of the detected threat when infected
     * @param engine    scanner identification stored with the document
     */
    record Result(Status status, String signature, String engine, Instant scannedAt) {
    }

    /** The scanner could not give an answer (not reachable, timeout, protocol error). */
    class UnavailableException extends RuntimeException {

        UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
