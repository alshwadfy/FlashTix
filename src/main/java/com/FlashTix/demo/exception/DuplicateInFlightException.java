package com.FlashTix.demo.exception;

public class DuplicateInFlightException extends RuntimeException {
    public DuplicateInFlightException(String message) {
        super(message);
    }
}
