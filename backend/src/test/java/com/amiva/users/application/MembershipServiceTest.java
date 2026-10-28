package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.MemberRoleChanged;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    MembershipRepository memberships;

    @Mock
    AppUserRepository users;

    @Mock
    OrgPermissionService permissions;

    @Mock
    ApplicationEventPublisher events;

    MembershipService service;

    private final AppUser ana = user("Ana");
    private final AppUser carlos = user("carlos");
    private final Organization org = withId(Organization.ownedBy(ana, "UTC"), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new MembershipService(memberships, users, permissions, events);
    }

    @Test
    void cambiarRolPublicaElEventoConRolAnteriorYNuevo() {
        givenMember(carlos, OrgRole.MEMBER);
        when(users.findById(ana.getId())).thenReturn(Optional.of(ana));

        service.changeRole(ana.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN);

        ArgumentCaptor<MemberRoleChanged> event = ArgumentCaptor.forClass(MemberRoleChanged.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new MemberRoleChanged(org.getId(), new Actor(ana.getId(), "Ana", false),
                carlos.getId(), "carlos", OrgRole.MEMBER, OrgRole.ADMIN));
    }

    @Test
    void mismoRolNoPublicaEvento() {
        givenMember(carlos, OrgRole.ADMIN);

        service.changeRole(ana.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void sinPermisoNoPublicaEvento() {
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.MANAGE_ROLES))
                .thenThrow(new PermissionDeniedException(OrgPermission.MANAGE_ROLES));

        assertThatThrownBy(() -> service.changeRole(ana.getId(), org.getId(), carlos.getId(), OrgRole.ADMIN))
                .isInstanceOf(PermissionDeniedException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    private void givenMember(AppUser user, OrgRole role) {
        Membership membership = Membership.member(org, user, role);
        when(memberships.findByOrganizationIdAndUserId(org.getId(), user.getId())).thenReturn(Optional.of(membership));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(memberships.saveAndFlush(membership)).thenReturn(membership);
    }

    private static AppUser user(String name) {
        return withId(AppUser.withPassword(name + "@example.com", "hash", name.toLowerCase(), name), UUID.randomUUID());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
