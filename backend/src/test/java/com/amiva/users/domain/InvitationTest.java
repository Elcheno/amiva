package com.amiva.users.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InvitationTest {

    private static final Instant SENT = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final Invitation invitation = Invitation.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            OrgRole.MEMBER, "a".repeat(64), SENT);

    @Test
    void nuevaEstaPendienteSinRespuesta() {
        assertThat(invitation.isPending()).isTrue();
        assertThat(invitation.getRespondedAt()).isNull();
    }

    @Test
    void aceptarDesdePendiente() {
        invitation.accept(NOW);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.isPending()).isFalse();
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
        assertThat(invitation.getUpdatedAt()).isEqualTo(NOW);
        assertThat(invitation.getCreatedAt()).isEqualTo(SENT);
    }

    @Test
    void rechazarDesdePendiente() {
        invitation.decline(NOW);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
    }

    @Test
    void noSeRespondeDosVecesDesdeAceptada() {
        invitation.accept(NOW);
        Instant later = NOW.plus(Duration.ofMinutes(1));

        assertThatThrownBy(() -> invitation.accept(later)).isInstanceOf(InvitationNotPendingException.class);
        assertThatThrownBy(() -> invitation.decline(later)).isInstanceOf(InvitationNotPendingException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
    }

    @Test
    void noSeRespondeDosVecesDesdeRechazada() {
        invitation.decline(NOW);
        Instant later = NOW.plus(Duration.ofMinutes(1));

        assertThatThrownBy(() -> invitation.accept(later)).isInstanceOf(InvitationNotPendingException.class);
        assertThatThrownBy(() -> invitation.decline(later)).isInstanceOf(InvitationNotPendingException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }
}
