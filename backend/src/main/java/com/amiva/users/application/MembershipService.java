package com.amiva.users.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    // OWNER primero, luego por nombre sin distinguir mayúsculas
    private static final Comparator<OrganizationMember> MEMBER_ORDER = Comparator
            .comparing((OrganizationMember m) -> m.role() != OrgRole.OWNER)
            .thenComparing(OrganizationMember::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(OrganizationMember::userId);

    private final MembershipRepository memberships;
    private final AppUserRepository users;

    public MembershipService(MembershipRepository memberships, AppUserRepository users) {
        this.memberships = memberships;
        this.users = users;
    }

    // Cualquier miembro de la organización puede ver la lista
    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(UUID actorId, UUID orgId) {
        requireMembership(actorId, orgId);

        List<Membership> members = memberships.findByOrganizationId(orgId);
        Map<UUID, AppUser> usersById = users.findAllById(members.stream().map(Membership::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return members.stream()
                .filter(m -> usersById.containsKey(m.getUserId()))
                .map(m -> toMember(m, usersById.get(m.getUserId())))
                .sorted(MEMBER_ORDER)
                .toList();
    }

    // Solo ADMIN o MEMBER como nuevo rol; el OWNER no se cambia
    @Transactional
    public OrganizationMember changeRole(UUID actorId, UUID orgId, UUID targetUserId, OrgRole newRole) {
        Membership actor = requireMembership(actorId, orgId);
        requireCanManageRoles(actor);

        Membership target = memberships.findByOrganizationIdAndUserId(orgId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);
        AppUser user = users.findById(targetUserId).orElseThrow(MemberNotFoundException::new);

        target.changeRole(newRole);
        // flush para que updated_at refleje el cambio en la respuesta
        target = memberships.saveAndFlush(target);
        return toMember(target, user);
    }

    // Organización inexistente y actor no miembro dan el mismo 404
    private Membership requireMembership(UUID actorId, UUID orgId) {
        return memberships.findByOrganizationIdAndUserId(orgId, actorId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    // Comprobación de permisos inline; se centralizará en la issue #8
    private static void requireCanManageRoles(Membership actor) {
        if (!actor.getRole().canManageRoles()) {
            throw new RoleChangeForbiddenException();
        }
    }

    private static OrganizationMember toMember(Membership membership, AppUser user) {
        return new OrganizationMember(user.getId(), user.getDisplayName(), user.getEmail(), membership.getRole(),
                membership.getCreatedAt());
    }
}
