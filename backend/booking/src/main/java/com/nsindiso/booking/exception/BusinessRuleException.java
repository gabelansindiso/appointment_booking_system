package com.nsindiso.booking.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {
    public BusinessRuleException (String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
