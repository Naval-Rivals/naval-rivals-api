package com.navalrivals.infra.exception.exceptions;

public class PlayerWithoutPermissionException extends RuntimeException {
    public PlayerWithoutPermissionException(String message) {
        super(message);
    }
}
