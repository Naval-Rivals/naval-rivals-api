package com.navalrivals.infra.exception.exceptions;

public class BadCredencialsException extends RuntimeException {
    public BadCredencialsException(String message) {
        super(message);
    }
}
