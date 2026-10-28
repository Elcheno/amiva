package com.amiva.users.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Invitation;
import com.amiva.users.domain.InvitationStatus;
import com.amiva.users.domain.MemberJoined;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.InvitationRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Invitaciones vistas por el invitado: listar, localizar por el token del email, aceptar y rechazar.
// Cualquier invitación que no sea suya y pendiente da el mismo 404
@Service
public class ReceivedInvitationService {

    private static final String MEMBERSHIP_PRIMARY_KEY = "pk_membership";

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReceivedInvitationService(InvitationRepository invitations, MembershipRepository memberships,
            AppUserRepository users, OrganizationRepository organizations, ApplicationEventPublisher events,
            Clock clock) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.organizations = organizations;
        this.events = events;
        this.clock = clock;
    }

    // Pendientes, más recientes primero. No exige email verificado (así se ve la invitación y el aviso)
    @Transactional(readOnly = true)
    public List<ReceivedInvitation> listMine(UUID userId) {
        List<Invitation> pending = invitations.findByInviteeUserIdAndStatusOrderByUpdatedAtDesc(userId,
                InvitationStatus.PENDING);
        if (pending.isEmpty()) {
            return List.of();
        }
        Map<UUID, Organization> orgsById = organizations
                .findAllById(pending.stream().map(Invitation::getOrganizationId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));
        Map<UUID, AppUser> invitersById = users
                .findAllById(pending.stream()
                        .map(Invitation::getInvitedByUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        // Sin organización no debería pasar (cascade); si pasa, se omite
        return pending.stream()
                .filter(i -> orgsById.containsKey(i.getOrganizationId()))
                .map(i -> toReceived(i, orgsById.get(i.getOrganizationId()), invitersById.get(i.getInvitedByUserId())))
                .toList();
    }

    // El token del email solo sirve para localizar la invitación; las respuestas van por id.
    // Un token reemplazado por un reenvío ya no existe en BD: 404
    @Transactional(readOnly = true)
    public ReceivedInvitation resolve(UUID userId, String token) {
        Invitation invitation = invitations.findByTokenHash(SecureTokens.hash(token))
                .filter(i -> isPendingFor(i, userId))
                .orElseThrow(InvitationNotFoundException::new);
        Organization org = organizations.findById(invitation.getOrganizationId())
                .orElseThrow(InvitationNotFoundException::new);
        AppUser inviter = invitation.getInvitedByUserId() == null ? null
                : users.findById(invitation.getInvitedByUserId()).orElse(null);
        return toReceived(invitation, org, inviter);
    }

    // Pasa a ser miembro con el rol de la invitación. Si ya lo es: 409 y la invitación queda como estaba
    @Transactional
    public MyOrganization accept(UUID userId, UUID invitationId) {
        AppUser user = requireVerified(userId);
        Invitation invitation = lockPending(userId, invitationId);
        Organization org = organizations.findById(invitation.getOrganizationId())
                .orElseThrow(InvitationNotFoundException::new);
        if (memberships.findByOrganizationIdAndUserId(org.getId(), userId).isPresent()) {
            throw new AlreadyMemberException();
        }

        invitation.accept(clock.instant());
        Membership membership = Membership.member(org, user, invitation.getRole());
        try {
            // flush para que una carrera con otra alta del mismo usuario salte aquí y no al hacer commit
            memberships.saveAndFlush(membership);
        } catch (DataIntegrityViolationException e) {
            if (isMembershipPrimaryKeyViolation(e)) {
                throw new AlreadyMemberException();
            }
            throw e;
        }
        events.publishEvent(new MemberJoined(org.getId(), user.asActor(), invitation.getRole()));

        return new MyOrganization(org.getId(), org.getName(), membership.getRole(), false);
    }

    // Sin actividad: rechazar no cambia nada en la organización
    @Transactional
    public void decline(UUID userId, UUID invitationId) {
        requireVerified(userId);
        lockPending(userId, invitationId).decline(clock.instant());
    }

    private AppUser requireVerified(UUID userId) {
        AppUser user = users.findById(userId)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Usuario no disponible"));
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return user;
    }

    // Bloqueo pesimista: de dos respuestas simultáneas, la segunda ya no la ve pendiente y da 404
    private Invitation lockPending(UUID userId, UUID invitationId) {
        return invitations.findByIdForUpdate(invitationId)
                .filter(i -> isPendingFor(i, userId))
                .orElseThrow(InvitationNotFoundException::new);
    }

    private static boolean isPendingFor(Invitation invitation, UUID userId) {
        return invitation.isPending() && invitation.getInviteeUserId().equals(userId);
    }

    private static boolean isMembershipPrimaryKeyViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && MEMBERSHIP_PRIMARY_KEY.equalsIgnoreCase(cve.getConstraintName());
    }

    private static ReceivedInvitation toReceived(Invitation invitation, Organization org, AppUser invitedBy) {
        return new ReceivedInvitation(invitation.getId(), org.getId(), org.getName(), invitation.getRole(),
                invitedBy == null ? null : invitedBy.getDisplayName(), invitation.getCreatedAt(),
                invitation.getUpdatedAt());
    }
}
