package com.mrms.shared.web;

import org.springframework.http.HttpStatus;

/**
 * Also used when a record exists but the caller may not see it, so that
 * record ids cannot be probed.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
    }
}
