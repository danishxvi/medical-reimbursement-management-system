package com.mrms.shared.web;

import org.springframework.http.HttpStatus;

/** A dependency the request needs (for example the virus scanner) is temporarily unavailable. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
