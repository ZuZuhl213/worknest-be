package com.hoang.worknest.exception;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException(String message) {
        super(message);
    }
}
