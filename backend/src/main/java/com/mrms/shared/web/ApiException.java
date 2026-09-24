package com.mrms.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that are safe to show to the user. The message is
 * returned to the client as is, so it must never contain internal details.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
