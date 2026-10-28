package com.amiva.users.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.amiva.users.application.InvalidVerificationTokenException.Reason;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.EmailVerificationToken;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.EmailVerificationTokenRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    static final Duration TOKEN_VALIDITY = Duration.ofHours(24);
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokens;
    private final AppUserRepository users;
    private final VerificationMailer mailer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(EmailVerificationTokenRepository tokens, AppUserRepository users,
            VerificationMailer mailer, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.mailer = mailer;
        this.clock = clock;
    }

    // Transacción propia: se llama desde un listener AFTER_COMMIT, donde ya no hay transacción activa.
    // Devuelve el token en claro (solo para el email)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issue(UUID userId) {
        return createToken(userId);
    }

    @Transactional
    public void verify(String token) {
        EmailVerificationToken stored = tokens.findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidVerificationTokenException(Reason.INVALID));
        Instant now = clock.instant();
        if (stored.isUsed()) {
            throw new InvalidVerificationTokenException(Reason.USED);
        }
        if (stored.isExpired(now)) {
            throw new InvalidVerificationTokenException(Reason.EXPIRED);
        }
        AppUser user = users.findById(stored.getUserId())
                .orElseThrow(() -> new InvalidVerificationTokenException(Reason.INVALID));
        user.verifyEmail();
        stored.markUsed(now);
    }

    // El email se envía dentro de la transacción: si falla el SMTP no queda token y se puede reintentar ya
    @Transactional
    public void resend(UUID userId) {
        AppUser user = users.findById(userId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Usuario no disponible"));
        if (user.isEmailVerified()) {
            throw new EmailAlreadyVerifiedException();
        }
        Instant now = clock.instant();
        tokens.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(last -> Duration.between(now, last.getCreatedAt().plus(RESEND_INTERVAL)))
                .filter(Duration::isPositive)
                .ifPresent(wait -> {
                    throw new VerificationResendTooSoonException(wait);
                });

        mailer.send(user.getEmail(), user.getDisplayName(), createToken(userId));
    }

    private String createToken(UUID userId) {
        tokens.deleteUnusedByUserId(userId);
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(EmailVerificationToken.issue(userId, hash(token), clock.instant(), TOKEN_VALIDITY));
        return token;
    }

    // SHA-256 en hex (64 caracteres): el token tiene 256 bits aleatorios, no hace falta sal ni KDF
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
