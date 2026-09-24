package com.mrms.shared.web;

import org.springframework.http.HttpStatus;

/**
 * A request that is well formed but breaks a business rule, for example
 * submitting a claim without a bill or acting out of queue order.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
