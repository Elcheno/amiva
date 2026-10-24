package com.amiva.users.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    // La propia primero, luego por nombre sin distinguir mayúsculas
    static final Comparator<MyOrganization> MY_ORGANIZATION_ORDER = Comparator
            .comparing((MyOrganization o) -> !o.own())
            .thenComparing(MyOrganization::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(MyOrganization::orgId);

    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final OrgPermissionService permissions;

    public OrganizationService(MembershipRepository memberships, OrganizationRepository organizations,
            OrgPermissionService permissions) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.permissions = permissions;
    }

    // Sin comprobación de permisos: solo devuelve las organizaciones donde el usuario es miembro
    @Transactional(readOnly = true)
    public List<MyOrganization> listMine(UUID userId) {
        List<Membership> mine = memberships.findByUserId(userId);
        Map<UUID, Organization> orgsById = organizations
                .findAllById(mine.stream().map(Membership::getOrganizationId).toList())
                .stream()
                .collect(Collectors.toMap(Organization::getId, Function.identity()));

        return mine.stream()
                .filter(m -> orgsById.containsKey(m.getOrganizationId()))
                .map(m -> toMyOrganization(m, orgsById.get(m.getOrganizationId())))
                .sorted(MY_ORGANIZATION_ORDER)
                .toList();
    }

    // Cualquier miembro la ve; si no existe o no eres miembro, 404
    @Transactional(readOnly = true)
    public MyOrganization findMine(UUID userId, UUID orgId) {
        Membership membership = permissions.require(userId, orgId, OrgPermission.VIEW_MEMBERS);
        Organization org = organizations.findById(orgId).orElseThrow(OrganizationNotFoundException::new);
        return toMyOrganization(membership, org);
    }

    private static MyOrganization toMyOrganization(Membership membership, Organization org) {
        return new MyOrganization(org.getId(), org.getName(), membership.getRole(), membership.isOwner());
    }
}
