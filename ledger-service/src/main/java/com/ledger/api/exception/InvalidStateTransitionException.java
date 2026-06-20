package com.ledger.api.exception;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
