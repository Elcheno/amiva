package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.amiva.users.application.InvalidVerificationTokenException.Reason;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.EmailVerificationToken;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.EmailVerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String TOKEN = "token-de-prueba";

    @Mock
    EmailVerificationTokenRepository tokens;

    @Mock
    AppUserRepository users;

    @Mock
    VerificationMailer mailer;

    EmailVerificationService service;

    private final UUID userId = UUID.randomUUID();
    private final AppUser user = withId(AppUser.withPassword("ana@example.com", "hash", "Ana"), userId);

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(tokens, users, mailer, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void tokenValidoVerificaElEmailYQuedaUsado() {
        EmailVerificationToken stored = EmailVerificationToken.issue(userId,
                EmailVerificationService.hash(TOKEN), NOW.minus(Duration.ofHours(1)), Duration.ofHours(24));
        when(tokens.findByTokenHash(EmailVerificationService.hash(TOKEN))).thenReturn(Optional.of(stored));
        when(users.findById(userId)).thenReturn(Optional.of(user));

        service.verify(TOKEN);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(stored.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void tokenInexistenteEsInvalid() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertReason(() -> service.verify(TOKEN), Reason.INVALID);
    }

    @Test
    void tokenCaducadoEsExpired() {
        // Emitido hace justo 24 h: caduca en este instante
        EmailVerificationToken stored = EmailVerificationToken.issue(userId,
                EmailVerificationService.hash(TOKEN), NOW.minus(Duration.ofHours(24)), Duration.ofHours(24));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertReason(() -> service.verify(TOKEN), Reason.EXPIRED);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(stored.isUsed()).isFalse();
    }

    @Test
    void tokenUsadoEsUsed() {
        EmailVerificationToken stored = EmailVerificationToken.issue(userId,
                EmailVerificationService.hash(TOKEN), NOW.minus(Duration.ofHours(1)), Duration.ofHours(24));
        stored.markUsed(NOW.minus(Duration.ofMinutes(30)));
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertReason(() -> service.verify(TOKEN), Reason.USED);
        assertThat(stored.getUsedAt()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
    }

    @Test
    void soloSeGuardaElHashDelToken() {
        String token = service.issue(userId);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(saved.capture());
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(EmailVerificationService.hash(token))
                .matches("[0-9a-f]{64}")
                .doesNotContain(token);
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void cadaEmisionGeneraUnTokenDistinto() {
        assertThat(service.issue(userId)).isNotEqualTo(service.issue(userId));
    }

    @Test
    void reenviarInvalidaLosAnterioresYEnviaUnoNuevo() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(tokens.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(
                EmailVerificationToken.issue(userId, "x".repeat(64), NOW.minus(Duration.ofSeconds(61)),
                        Duration.ofHours(24))));

        service.resend(userId);

        InOrder order = inOrder(tokens, mailer);
        order.verify(tokens).deleteUnusedByUserId(userId);
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        order.verify(tokens).save(saved.capture());
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        order.verify(mailer).send(eq("ana@example.com"), eq("Ana"), sent.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(EmailVerificationService.hash(sent.getValue()));
    }

    @Test
    void reenviarAntesDeUnMinutoEs429() {
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(tokens.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(
                EmailVerificationToken.issue(userId, "x".repeat(64), NOW.minus(Duration.ofSeconds(59)),
                        Duration.ofHours(24))));

        assertThatThrownBy(() -> service.resend(userId))
                .isInstanceOfSatisfying(VerificationResendTooSoonException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(1)));

        verify(tokens, never()).deleteUnusedByUserId(any());
        verify(tokens, never()).save(any());
        verify(mailer, never()).send(any(), any(), any());
    }

    @Test
    void reenviarConEmailYaVerificadoEs409() {
        user.verifyEmail();
        when(users.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resend(userId)).isInstanceOf(EmailAlreadyVerifiedException.class);

        verify(tokens, never()).save(any());
        verify(mailer, never()).send(any(), any(), any());
    }

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(InvalidVerificationTokenException.class,
                        e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
