package com.amiva.users.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Invitaciones a una organización para usuarios ya registrados
@Service
public class InvitationService {

    static final int SEARCH_MIN_LENGTH = 3;
    static final int SEARCH_MAX_RESULTS = 10;

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final OrgPermissionService permissions;
    private final InvitationMailer mailer;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, MembershipRepository memberships,
            AppUserRepository users, OrganizationRepository organizations, OrgPermissionService permissions,
            InvitationMailer mailer, ApplicationEventPublisher events, Clock clock) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.users = users;
        this.organizations = organizations;
        this.permissions = permissions;
        this.mailer = mailer;
        this.events = events;
        this.clock = clock;
    }

    // Invitar con rol ADMIN exige además MANAGE_ROLES (solo el OWNER da ADMIN, como al cambiar roles).
    // Si ya hay una pendiente para ese usuario se reutiliza: token, rol e invitador nuevos y se reenvía el email.
    // El email se envía dentro de la transacción: si falla el SMTP no queda invitación ni actividad
    @Transactional
    public PendingInvitation invite(UUID actorId, UUID orgId, UUID inviteeUserId, OrgRole role) {
        Membership actorMembership = permissions.require(actorId, orgId, OrgPermission.INVITE_MEMBERS);
        if (role == OrgRole.ADMIN && !actorMembership.getRole().has(OrgPermission.MANAGE_ROLES)) {
            throw new PermissionDeniedException(OrgPermission.MANAGE_ROLES);
        }

        AppUser invitee = users.findById(inviteeUserId)
                .filter(AppUser::isEnabled)
                .orElseThrow(UserNotFoundException::new);
        if (memberships.findByOrganizationIdAndUserId(orgId, inviteeUserId).isPresent()) {
            throw new AlreadyMemberException();
        }
        // El actor ya pasó require: es miembro y la organización existe
        Organization org = organizations.findById(orgId).orElseThrow(OrganizationNotFoundException::new);
        AppUser actor = users.findById(actorId).orElseThrow();

        String token = SecureTokens.generate();
        String tokenHash = SecureTokens.hash(token);
        Instant now = clock.instant();
        Invitation invitation = invitations
                .findByOrganizationIdAndInviteeUserIdAndStatus(orgId, inviteeUserId, InvitationStatus.PENDING)
                .map(pending -> {
                    pending.reissue(actorId, role, tokenHash, now);
                    return pending;
                })
                .orElseGet(() -> Invitation.pending(orgId, inviteeUserId, actorId, role, tokenHash, now));
        // flush antes del email: si choca con otra invitación simultánea, falla sin haber enviado nada
        invitation = invitations.saveAndFlush(invitation);

        events.publishEvent(new MemberInvited(orgId, actor.asActor(), invitee.getId(), invitee.getDisplayName(), role));

        try {
            mailer.send(invitee.getEmail(), invitee.getDisplayName(), org.getName(), actor.getDisplayName(), role,
                    token);
        } catch (MailException e) {
            throw new InvitationEmailException(e);
        }
        return toPending(invitation, invitee, actor);
    }

    // Más recientes primero (también las reenviadas)
    @Transactional(readOnly = true)
    public List<PendingInvitation> listPending(UUID actorId, UUID orgId) {
        permissions.require(actorId, orgId, OrgPermission.INVITE_MEMBERS);

        List<Invitation> pending = invitations.findByOrganizationIdAndStatusOrderByUpdatedAtDesc(orgId,
                InvitationStatus.PENDING);
        Set<UUID> userIds = pending.stream()
                .flatMap(i -> Stream.of(i.getInviteeUserId(), i.getInvitedByUserId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> usersById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return pending.stream()
                .filter(i -> usersById.containsKey(i.getInviteeUserId()))
                .map(i -> toPending(i, usersById.get(i.getInviteeUserId()), usersById.get(i.getInvitedByUserId())))
                .toList();
    }

    // Usuarios registrados que aún no son miembros: username que empieza por q o nombre que contiene q.
    // Menos de 3 caracteres: lista vacía (no sirve para recorrer todos los usuarios)
    @Transactional(readOnly = true)
    public List<InvitableUser> searchInvitable(UUID actorId, UUID orgId, String query) {
        permissions.require(actorId, orgId, OrgPermission.INVITE_MEMBERS);

        String q = query == null ? "" : query.strip();
        if (q.codePointCount(0, q.length()) < SEARCH_MIN_LENGTH) {
            return List.of();
        }
        String escaped = escapeLike(q.toLowerCase(Locale.ROOT));
        List<AppUser> found = users.searchInvitable(orgId, escaped + "%", "%" + escaped + "%",
                Limit.of(SEARCH_MAX_RESULTS));
        if (found.isEmpty()) {
            return List.of();
        }
        Set<UUID> pending = new HashSet<>(invitations.findPendingInviteeIds(orgId,
                found.stream().map(AppUser::getId).toList()));

        return found.stream()
                .map(u -> new InvitableUser(u.getId(), u.getUsername(), u.getDisplayName(), pending.contains(u.getId())))
                .toList();
    }

    // Comodines de LIKE como texto literal (escape '\' en la consulta)
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static PendingInvitation toPending(Invitation invitation, AppUser invitee, AppUser invitedBy) {
        return new PendingInvitation(invitation.getId(), invitee.getId(), invitee.getUsername(),
                invitee.getDisplayName(), invitation.getRole(), invitedBy == null ? null : invitedBy.getDisplayName(),
                invitation.getCreatedAt(), invitation.getUpdatedAt());
    }
}
