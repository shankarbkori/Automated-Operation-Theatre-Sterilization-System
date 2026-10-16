package com.ot.backend.exception;

public class InvalidSensorException extends RuntimeException {
    public InvalidSensorException(String message) {
        super(message);
    }
}
