package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrgPermissionServiceTest {

    @Mock
    MembershipRepository memberships;

    OrgPermissionService service;

    private final AppUser owner = withId(AppUser.withPassword("ana@example.com", "hash", "ana", "Ana"), UUID.randomUUID());
    private final AppUser admin = withId(AppUser.withPassword("bea@example.com", "hash", "bea", "Bea"), UUID.randomUUID());
    private final Organization org = withId(Organization.ownedBy(owner, "UTC"), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new OrgPermissionService(memberships);
    }

    @Test
    void devuelveLaMembresiaSiTieneElPermiso() {
        Membership membership = Membership.owner(org);
        when(memberships.findByOrganizationIdAndUserId(org.getId(), owner.getId()))
                .thenReturn(Optional.of(membership));

        assertThat(service.require(owner.getId(), org.getId(), OrgPermission.MANAGE_ROLES)).isSameAs(membership);
    }

    @Test
    void sinPermisoLanzaPermissionDenied() {
        when(memberships.findByOrganizationIdAndUserId(org.getId(), admin.getId()))
                .thenReturn(Optional.of(Membership.member(org, admin, OrgRole.ADMIN)));

        assertThatThrownBy(() -> service.require(admin.getId(), org.getId(), OrgPermission.MANAGE_ROLES))
                .isInstanceOfSatisfying(PermissionDeniedException.class,
                        e -> assertThat(e.getPermission()).isEqualTo(OrgPermission.MANAGE_ROLES));
    }

    @Test
    void noMiembroLanzaOrganizacionNoEncontrada() {
        UUID stranger = UUID.randomUUID();
        when(memberships.findByOrganizationIdAndUserId(org.getId(), stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(stranger, org.getId(), OrgPermission.VIEW_MEMBERS))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
