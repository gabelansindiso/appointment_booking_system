package com.nsindiso.booking.exception;

import org.springframework.http.HttpStatus;

public class SlotUnavailableException extends ApiException {
    public SlotUnavailableException (String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
