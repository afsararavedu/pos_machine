package com.aifsar.pos.auth.service;

/** Authentication failure. Declared as no-rollback so failed-attempt counters and revocations still commit. */
public class AuthFailedException extends RuntimeException {

    public AuthFailedException(String message) {
        super(message);
    }
}
