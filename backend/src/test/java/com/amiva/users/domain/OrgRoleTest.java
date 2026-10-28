package com.amiva.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrgRoleTest {

    @Test
    void todosLosRolesVenLosMiembros() {
        for (OrgRole role : OrgRole.values()) {
            assertThat(role.has(OrgPermission.VIEW_MEMBERS)).as(role.name()).isTrue();
        }
    }

    @Test
    void soloElOwnerCambiaRoles() {
        assertThat(OrgRole.OWNER.has(OrgPermission.MANAGE_ROLES)).isTrue();
        assertThat(OrgRole.ADMIN.has(OrgPermission.MANAGE_ROLES)).isFalse();
        assertThat(OrgRole.MEMBER.has(OrgPermission.MANAGE_ROLES)).isFalse();
    }

    @Test
    void ownerYAdminInvitan() {
        assertThat(OrgRole.OWNER.has(OrgPermission.INVITE_MEMBERS)).isTrue();
        assertThat(OrgRole.ADMIN.has(OrgPermission.INVITE_MEMBERS)).isTrue();
        assertThat(OrgRole.MEMBER.has(OrgPermission.INVITE_MEMBERS)).isFalse();
    }
}
