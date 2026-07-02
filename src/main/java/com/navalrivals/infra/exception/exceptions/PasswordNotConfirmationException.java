package com.navalrivals.infra.exception.exceptions;

public class PasswordNotConfirmationException extends RuntimeException {
    public PasswordNotConfirmationException(String message) {
        super(message);
    }
}
