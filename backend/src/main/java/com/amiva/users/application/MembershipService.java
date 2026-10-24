package com.amiva.users.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
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
    private final OrgPermissionService permissions;

    public MembershipService(MembershipRepository memberships, AppUserRepository users,
            OrgPermissionService permissions) {
        this.memberships = memberships;
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(UUID actorId, UUID orgId) {
        permissions.require(actorId, orgId, OrgPermission.VIEW_MEMBERS);

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
        permissions.require(actorId, orgId, OrgPermission.MANAGE_ROLES);

        Membership target = memberships.findByOrganizationIdAndUserId(orgId, targetUserId)
                .orElseThrow(MemberNotFoundException::new);
        AppUser user = users.findById(targetUserId).orElseThrow(MemberNotFoundException::new);

        target.changeRole(newRole);
        // flush para que updated_at refleje el cambio en la respuesta
        target = memberships.saveAndFlush(target);
        return toMember(target, user);
    }

    private static OrganizationMember toMember(Membership membership, AppUser user) {
        return new OrganizationMember(user.getId(), user.getDisplayName(), user.getEmail(), membership.getRole(),
                membership.getCreatedAt());
    }
}
