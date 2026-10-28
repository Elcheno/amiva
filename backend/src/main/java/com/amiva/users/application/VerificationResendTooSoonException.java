package com.amiva.users.application;

import java.time.Duration;

public class VerificationResendTooSoonException extends RuntimeException {

    private final Duration retryAfter;

    public VerificationResendTooSoonException(Duration retryAfter) {
        super("Espera un minuto antes de pedir otro email de verificación");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
