package com.navalrivals.infra.exception.exceptions;

public class RoomFullException extends RuntimeException {
    public RoomFullException(String message) {
        super(message);
    }
}
