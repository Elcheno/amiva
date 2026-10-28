package com.amiva.users.application;

// Envía el email con el enlace de verificación (implementación SMTP en infrastructure)
public interface VerificationMailer {

    void send(String to, String displayName, String token);
}
