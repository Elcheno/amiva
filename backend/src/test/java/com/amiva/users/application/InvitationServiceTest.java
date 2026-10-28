package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Invitation;
import com.amiva.users.domain.InvitationStatus;
import com.amiva.users.domain.MemberInvited;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
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
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

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
    OrgPermissionService permissions;

    @Mock
    InvitationMailer mailer;

    @Mock
    ApplicationEventPublisher events;

    InvitationService service;

    private final AppUser ana = user("Ana");
    private final AppUser bea = user("Bea");
    private final AppUser carlos = user("carlos");
    private final Organization org = withId(Organization.ownedBy(ana, "UTC"), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitations, memberships, users, organizations, permissions, mailer, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adminInvitaComoMemberGuardaElHashYEnviaElToken() {
        givenActor(bea, OrgRole.ADMIN);
        givenInvitable(carlos, Optional.empty());

        PendingInvitation result = service.invite(bea.getId(), org.getId(), carlos.getId(), OrgRole.MEMBER);

        Invitation saved = savedInvitation();
        assertThat(saved.getOrganizationId()).isEqualTo(org.getId());
        assertThat(saved.getInviteeUserId()).isEqualTo(carlos.getId());
        assertThat(saved.getInvitedByUserId()).isEqualTo(bea.getId());
        assertThat(saved.getRole()).isEqualTo(OrgRole.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        String token = sentToken(carlos);
        assertThat(saved.getTokenHash()).isEqualTo(SecureTokens.hash(token)).isNotEqualTo(token);
        assertThat(result.invitedByName()).isEqualTo("Bea");
        assertThat(result.username()).isEqualTo("carlos");
    }

    @Test
    void publicaMemberInvited() {
        givenActor(bea, OrgRole.ADMIN);
        givenInvitable(carlos, Optional.empty());

        service.invite(bea.getId(), org.getId(), carlos.getId(), OrgRole.MEMBER);

        ArgumentCaptor<MemberInvited> event = ArgumentCaptor.forClass(MemberInvited.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new MemberInvited(org.getId(), new Actor(bea.getId(), "Bea", false),
                carlos.getId(), "carlos", OrgRole.MEMBER));
    }

    @Test
    void adminNoPuedeInvitarComoAdmin() {
        givenActor(bea, OrgRole.ADMIN);

        assertThatThrownBy(() -> service.invite(bea.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN))
                .isInstanceOfSatisfying(PermissionDeniedException.class,
                        e -> assertThat(e.getPermission()).isEqualTo(OrgPermission.MANAGE_ROLES));

        verifyNothingDone();
    }

    @Test
    void ownerPuedeInvitarComoAdmin() {
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.INVITE_MEMBERS))
                .thenReturn(Membership.owner(org));
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));
        givenInvitable(carlos, Optional.empty());

        service.invite(ana.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN);

        assertThat(savedInvitation().getRole()).isEqualTo(OrgRole.ADMIN);
    }

    @Test
    void memberNoPuedeInvitar() {
        when(permissions.require(carlos.getId(), org.getId(), OrgPermission.INVITE_MEMBERS))
                .thenThrow(new PermissionDeniedException(OrgPermission.INVITE_MEMBERS));

        assertThatThrownBy(() -> service.invite(carlos.getId(), org.getId(), bea.getId(), OrgRole.MEMBER))
                .isInstanceOfSatisfying(PermissionDeniedException.class,
                        e -> assertThat(e.getPermission()).isEqualTo(OrgPermission.INVITE_MEMBERS));

        verifyNothingDone();
    }

    @Test
    void yaMiembroDaConflicto() {
        givenActor(bea, OrgRole.ADMIN);
        when(users.findById(carlos.getId())).thenReturn(Optional.of(carlos));
        when(memberships.findByOrganizationIdAndUserId(org.getId(), carlos.getId()))
                .thenReturn(Optional.of(Membership.member(org, carlos, OrgRole.MEMBER)));

        assertThatThrownBy(() -> service.invite(bea.getId(), org.getId(), carlos.getId(), OrgRole.MEMBER))
                .isInstanceOf(AlreadyMemberException.class);

        verifyNothingDone();
    }

    @Test
    void usuarioInexistente() {
        givenActor(bea, OrgRole.ADMIN);
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(bea.getId(), org.getId(), unknown, OrgRole.MEMBER))
                .isInstanceOf(UserNotFoundException.class);

        verifyNothingDone();
    }

    @Test
    void reenvioReutilizaLaFilaConTokenYRolNuevos() {
        Instant earlier = NOW.minus(Duration.ofDays(2));
        Invitation pending = Invitation.pending(org.getId(), carlos.getId(), bea.getId(), OrgRole.MEMBER,
                "a".repeat(64), earlier);
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.INVITE_MEMBERS))
                .thenReturn(Membership.owner(org));
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));
        givenInvitable(carlos, Optional.of(pending));

        service.invite(ana.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN);

        Invitation saved = savedInvitation();
        assertThat(saved).isSameAs(pending);
        assertThat(saved.getTokenHash()).isNotEqualTo("a".repeat(64)).isEqualTo(SecureTokens.hash(sentToken(carlos)));
        assertThat(saved.getRole()).isEqualTo(OrgRole.ADMIN);
        assertThat(saved.getInvitedByUserId()).isEqualTo(ana.getId());
        assertThat(saved.getCreatedAt()).isEqualTo(earlier);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        verify(events).publishEvent(any(Object.class));
    }

    // La excepción sube para que la transacción haga rollback (sin invitación ni actividad)
    @Test
    void siFallaElEmailLaExcepcionSube() {
        givenActor(bea, OrgRole.ADMIN);
        givenInvitable(carlos, Optional.empty());
        MailSendException smtpDown = new MailSendException("SMTP caído");
        doThrow(smtpDown).when(mailer).send(anyString(), anyString(), anyString(), anyString(), any(), anyString());

        assertThatThrownBy(() -> service.invite(bea.getId(), org.getId(), carlos.getId(), OrgRole.MEMBER))
                .isInstanceOf(InvitationEmailException.class)
                .hasCause(smtpDown);
    }

    @Test
    void busquedaCortaNoConsulta() {
        assertThat(service.searchInvitable(bea.getId(), org.getId(), "  ab  ")).isEmpty();
        assertThat(service.searchInvitable(bea.getId(), org.getId(), null)).isEmpty();

        verify(permissions, times(2)).require(bea.getId(), org.getId(),
                OrgPermission.INVITE_MEMBERS);
        verifyNoInteractions(users, invitations);
    }

    @Test
    void busquedaEnMinusculasConComodinesEscapadosYMarcaPendientes() {
        when(users.searchInvitable(eq(org.getId()), eq("50\\%\\_a\\\\%"), eq("%50\\%\\_a\\\\%"), any()))
                .thenReturn(List.of(bea, carlos));
        when(invitations.findPendingInviteeIds(org.getId(), List.of(bea.getId(), carlos.getId())))
                .thenReturn(List.of(carlos.getId()));

        List<InvitableUser> found = service.searchInvitable(ana.getId(), org.getId(), " 50%_A\\ ");

        assertThat(found).containsExactly(new InvitableUser(bea.getId(), "bea", "Bea", false),
                new InvitableUser(carlos.getId(), "carlos", "carlos", true));
    }

    private void givenActor(AppUser actor, OrgRole role) {
        when(permissions.require(actor.getId(), org.getId(), OrgPermission.INVITE_MEMBERS))
                .thenReturn(Membership.member(org, actor, role));
        // Solo se carga si llega a invitar (lenient: los casos 403/404/409 no lo usan)
        lenient().when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private void givenInvitable(AppUser invitee, Optional<Invitation> pending) {
        when(users.findById(invitee.getId())).thenReturn(Optional.of(invitee));
        when(memberships.findByOrganizationIdAndUserId(org.getId(), invitee.getId())).thenReturn(Optional.empty());
        when(organizations.findById(org.getId())).thenReturn(Optional.of(org));
        when(invitations.findByOrganizationIdAndInviteeUserIdAndStatus(org.getId(), invitee.getId(),
                InvitationStatus.PENDING)).thenReturn(pending);
        when(invitations.saveAndFlush(any(Invitation.class))).thenAnswer(returnsFirstArg());
    }

    private Invitation savedInvitation() {
        ArgumentCaptor<Invitation> invitation = ArgumentCaptor.forClass(Invitation.class);
        verify(invitations).saveAndFlush(invitation.capture());
        return invitation.getValue();
    }

    private String sentToken(AppUser invitee) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq(invitee.getEmail()), eq(invitee.getDisplayName()), eq(org.getName()), anyString(),
                any(), token.capture());
        return token.getValue();
    }

    private void verifyNothingDone() {
        verify(invitations, never()).saveAndFlush(any(Invitation.class));
        verify(events, never()).publishEvent(any(Object.class));
        verifyNoInteractions(mailer);
    }

    private static AppUser user(String name) {
        return withId(AppUser.withPassword(name + "@example.com", "hash", name.toLowerCase(), name), UUID.randomUUID());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
