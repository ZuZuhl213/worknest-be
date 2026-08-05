package com.hoang.worknest.exception;

public class GoogleAuthenticationException extends RuntimeException {

    public GoogleAuthenticationException() {
        super("Google authentication failed");
    }
}
