package com.amiva.users.application;

import com.amiva.users.domain.UserRegistered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Tras el commit del registro: crea el token y envía el email. Síncrono.
// Si falla (SMTP caído...) solo se registra el error: el registro ya está hecho y el usuario puede pedir otro
@Component
public class VerificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailListener.class);

    private final EmailVerificationService verifications;
    private final VerificationMailer mailer;

    public VerificationEmailListener(EmailVerificationService verifications, VerificationMailer mailer) {
        this.verifications = verifications;
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRegistered event) {
        try {
            String token = verifications.issue(event.userId());
            mailer.send(event.email(), event.displayName(), token);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el email de verificación al usuario {}", event.userId(), e);
        }
    }
}
