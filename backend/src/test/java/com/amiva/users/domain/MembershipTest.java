package com.amiva.users.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MembershipTest {

    private final AppUser owner = withId(AppUser.withPassword("ana@example.com", "hash", "ana", "Ana"), UUID.randomUUID());
    private final AppUser other = withId(AppUser.withPassword("bea@example.com", "hash", "bea", "Bea"), UUID.randomUUID());
    private final Organization org = withId(Organization.ownedBy(owner, "UTC"), UUID.randomUUID());

    @Test
    void ownerEsElDuenoDeLaOrganizacion() {
        Membership membership = Membership.owner(org);

        assertThat(membership.getOrganizationId()).isEqualTo(org.getId());
        assertThat(membership.getUserId()).isEqualTo(owner.getId());
        assertThat(membership.getRole()).isEqualTo(OrgRole.OWNER);
    }

    @Test
    void elRolDelOwnerNoSeCambia() {
        Membership membership = Membership.owner(org);

        assertThatThrownBy(() -> membership.changeRole(OrgRole.ADMIN)).isInstanceOf(OwnerRoleImmutableException.class);
        assertThat(membership.getRole()).isEqualTo(OrgRole.OWNER);
    }

    @Test
    void noSeAsignaOwner() {
        Membership membership = Membership.member(org, other, OrgRole.MEMBER);

        assertThatThrownBy(() -> membership.changeRole(OrgRole.OWNER)).isInstanceOf(RoleNotAssignableException.class);
        assertThatThrownBy(() -> Membership.member(org, other, OrgRole.OWNER))
                .isInstanceOf(RoleNotAssignableException.class);
        assertThat(membership.getRole()).isEqualTo(OrgRole.MEMBER);
    }

    @Test
    void adminYMemberSeIntercambian() {
        Membership membership = Membership.member(org, other, OrgRole.MEMBER);

        membership.changeRole(OrgRole.ADMIN);
        assertThat(membership.getRole()).isEqualTo(OrgRole.ADMIN);

        membership.changeRole(OrgRole.MEMBER);
        assertThat(membership.getRole()).isEqualTo(OrgRole.MEMBER);
    }

    @Test
    void rolAsignableDesdeTexto() {
        assertThat(OrgRole.assignable("ADMIN")).isEqualTo(OrgRole.ADMIN);
        assertThat(OrgRole.assignable(" member ")).isEqualTo(OrgRole.MEMBER);
        assertThatThrownBy(() -> OrgRole.assignable("OWNER")).isInstanceOf(RoleNotAssignableException.class);
        assertThatThrownBy(() -> OrgRole.assignable("JEFE")).isInstanceOf(RoleNotAssignableException.class);
        assertThatThrownBy(() -> OrgRole.assignable(null)).isInstanceOf(RoleNotAssignableException.class);
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
