package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Invitation;
import com.amiva.users.domain.InvitationStatus;
import com.amiva.users.domain.MemberJoined;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.InvitationRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReceivedInvitationServiceTest {

    private static final Instant SENT = Instant.parse("2026-10-20T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Mock
    InvitationRepository invitations;

    @Mock
    MembershipRepository memberships;

    @Mock
    AppUserRepository users;

    @Mock
    OrganizationRepository organizations;

    @Mock
    ApplicationEventPublisher events;

    ReceivedInvitationService service;

    private final AppUser ana = user("Ana");
    private final AppUser carlos = user("Carlos");
    private final Organization org = withId(Organization.ownedBy(ana, "UTC"), UUID.randomUUID());
    private final String token = SecureTokens.generate();
    private Invitation invitation;

    @BeforeEach
    void setUp() {
        service = new ReceivedInvitationService(invitations, memberships, users, organizations, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        invitation = withId(Invitation.pending(org.getId(), carlos.getId(), ana.getId(), OrgRole.ADMIN,
                SecureTokens.hash(token), SENT), UUID.randomUUID());
    }

    @Test
    void listaMisPendientesConOrganizacionEInvitador() {
        when(invitations.findByInviteeUserIdAndStatusOrderByUpdatedAtDesc(carlos.getId(), InvitationStatus.PENDING))
                .thenReturn(List.of(invitation));
        when(organizations.findAllById(Set.of(org.getId()))).thenReturn(List.of(org));
        when(users.findAllById(Set.of(ana.getId()))).thenReturn(List.of(ana));

        assertThat(service.listMine(carlos.getId())).containsExactly(new ReceivedInvitation(invitation.getId(),
                org.getId(), org.getName(), OrgRole.ADMIN, "Ana", SENT, SENT));
    }

    @Test
    void resolveLocalizaPorElHashDelToken() {
        when(invitations.findByTokenHash(SecureTokens.hash(token))).thenReturn(Optional.of(invitation));
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));

        ReceivedInvitation resolved = service.resolve(carlos.getId(), token);

        assertThat(resolved.id()).isEqualTo(invitation.getId());
        assertThat(resolved.organizationName()).isEqualTo(org.getName());
        assertThat(resolved.invitedByName()).isEqualTo("Ana");
    }

    @Test
    void resolveDeOtroUsuarioNoSeEncuentra() {
        when(invitations.findByTokenHash(SecureTokens.hash(token))).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.resolve(ana.getId(), token)).isInstanceOf(InvitationNotFoundException.class);
    }

    @Test
    void aceptarCreaLaMembresiaConElRolYPublicaMemberJoined() {
        givenVerified(carlos);
        givenLocked(invitation);
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(memberships.findByOrganizationIdAndUserId(org.getId(), carlos.getId())).thenReturn(Optional.empty());

        MyOrganization joined = service.accept(carlos.getId(), invitation.getId());

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(memberships).saveAndFlush(membership.capture());
        assertThat(membership.getValue().getOrganizationId()).isEqualTo(org.getId());
        assertThat(membership.getValue().getUserId()).isEqualTo(carlos.getId());
        assertThat(membership.getValue().getRole()).isEqualTo(OrgRole.ADMIN);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new MemberJoined(org.getId(), new Actor(carlos.getId(), "Carlos", false),
                OrgRole.ADMIN));
        assertThat(joined).isEqualTo(new MyOrganization(org.getId(), org.getName(), OrgRole.ADMIN, false));
    }

    @Test
    void rechazarMarcaLaInvitacionYNoPublicaNada() {
        givenVerified(carlos);
        givenLocked(invitation);

        service.decline(carlos.getId(), invitation.getId());

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        assertThat(invitation.getRespondedAt()).isEqualTo(NOW);
        verifyNoInteractions(events, memberships);
    }

    @Test
    void invitacionDeOtroUsuarioNoSeEncuentra() {
        givenVerified(ana);
        givenLocked(invitation);

        assertThatThrownBy(() -> service.accept(ana.getId(), invitation.getId()))
                .isInstanceOf(InvitationNotFoundException.class);
        assertThatThrownBy(() -> service.decline(ana.getId(), invitation.getId()))
                .isInstanceOf(InvitationNotFoundException.class);

        assertThat(invitation.isPending()).isTrue();
        verifyNothingDone();
    }

    @Test
    void invitacionInexistenteNoSeEncuentra() {
        givenVerified(carlos);
        UUID unknown = UUID.randomUUID();
        when(invitations.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(carlos.getId(), unknown))
                .isInstanceOf(InvitationNotFoundException.class);

        verifyNothingDone();
    }

    @Test
    void invitacionYaRespondidaNoSeEncuentra() {
        givenVerified(carlos);
        invitation.decline(SENT);
        givenLocked(invitation);

        assertThatThrownBy(() -> service.accept(carlos.getId(), invitation.getId()))
                .isInstanceOf(InvitationNotFoundException.class);
        assertThatThrownBy(() -> service.decline(carlos.getId(), invitation.getId()))
                .isInstanceOf(InvitationNotFoundException.class);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        verifyNothingDone();
    }

    @Test
    void sinEmailVerificadoNoSeResponde() {
        when(users.findById(carlos.getId())).thenReturn(Optional.of(carlos));

        assertThatThrownBy(() -> service.accept(carlos.getId(), invitation.getId()))
                .isInstanceOf(EmailNotVerifiedException.class);
        assertThatThrownBy(() -> service.decline(carlos.getId(), invitation.getId()))
                .isInstanceOf(EmailNotVerifiedException.class);

        assertThat(invitation.isPending()).isTrue();
        verify(invitations, never()).findByIdForUpdate(any());
        verifyNothingDone();
    }

    // La excepción sube para que la transacción haga rollback: la invitación sigue pendiente
    @Test
    void yaMiembroDaConflictoSinTocarLaInvitacion() {
        givenVerified(carlos);
        givenLocked(invitation);
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(memberships.findByOrganizationIdAndUserId(org.getId(), carlos.getId()))
                .thenReturn(Optional.of(Membership.member(org, carlos, OrgRole.MEMBER)));

        assertThatThrownBy(() -> service.accept(carlos.getId(), invitation.getId()))
                .isInstanceOf(AlreadyMemberException.class);

        assertThat(invitation.isPending()).isTrue();
        verify(memberships, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    private void givenVerified(AppUser user) {
        user.verifyEmail();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void givenLocked(Invitation invitation) {
        when(invitations.findByIdForUpdate(invitation.getId())).thenReturn(Optional.of(invitation));
    }

    private void verifyNothingDone() {
        verify(memberships, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    private static AppUser user(String name) {
        return withId(AppUser.withPassword(name + "@example.com", "hash", name.toLowerCase(), name), UUID.randomUUID());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
