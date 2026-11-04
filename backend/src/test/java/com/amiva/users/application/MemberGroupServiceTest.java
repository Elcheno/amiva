package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.GroupCreated;
import com.amiva.users.domain.GroupDeleted;
import com.amiva.users.domain.GroupMemberAdded;
import com.amiva.users.domain.GroupMemberRemoved;
import com.amiva.users.domain.GroupRenamed;
import com.amiva.users.domain.InvalidGroupNameException;
import com.amiva.users.domain.MemberGroup;
import com.amiva.users.domain.MemberGroupMember;
import com.amiva.users.domain.MemberGroupMemberId;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MemberGroupMemberRepository;
import com.amiva.users.infrastructure.MemberGroupRepository;
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
class MemberGroupServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Mock
    MemberGroupRepository groups;

    @Mock
    MemberGroupMemberRepository groupMembers;

    @Mock
    MembershipRepository memberships;

    @Mock
    AppUserRepository users;

    @Mock
    OrgPermissionService permissions;

    @Mock
    ApplicationEventPublisher events;

    MemberGroupService service;

    private final AppUser ana = user("Ana");
    private final AppUser bea = user("Bea");
    private final Organization org = withId(Organization.ownedBy(ana, "UTC"), UUID.randomUUID());
    private final Actor anaActor = new Actor(ana.getId(), "Ana", false);

    @BeforeEach
    void setUp() {
        service = new MemberGroupService(groups, groupMembers, memberships, users, permissions, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        // Solo se carga si llega a emitir el evento (lenient: los casos 403/404/409/400 no lo usan)
        lenient().when(users.findById(ana.getId())).thenReturn(Optional.of(ana));
    }

    @Test
    void creaNormalizandoElNombreYPublicaGroupCreated() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        when(groups.saveAndFlush(any())).thenAnswer(returnsFirstArg());

        GroupSummary result = service.create(ana.getId(), org.getId(), "  Equipo   de\tventas ");

        assertThat(result.name()).isEqualTo("Equipo de ventas");
        assertThat(result.memberCount()).isZero();
        ArgumentCaptor<MemberGroup> saved = ArgumentCaptor.forClass(MemberGroup.class);
        verify(groups).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(org.getId());
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new GroupCreated(org.getId(), anaActor, saved.getValue().getId(),
                "Equipo de ventas"));
    }

    @Test
    void nombreVacioONuloDa400() {
        givenActor(OrgPermission.MANAGE_GROUPS);

        assertThatThrownBy(() -> service.create(ana.getId(), org.getId(), "  \t ")).isInstanceOf(
                InvalidGroupNameException.class);
        assertThatThrownBy(() -> service.create(ana.getId(), org.getId(), null)).isInstanceOf(
                InvalidGroupNameException.class);

        verifyNothingSaved();
    }

    @Test
    void elLimiteDeNombreSeCuentaEnCaracteresNoEnBytes() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        when(groups.saveAndFlush(any())).thenAnswer(returnsFirstArg());

        assertThat(service.create(ana.getId(), org.getId(), "ñ".repeat(60)).name()).hasSize(60);
        assertThatThrownBy(() -> service.create(ana.getId(), org.getId(), "ñ".repeat(61)))
                .isInstanceOf(InvalidGroupNameException.class);
    }

    @Test
    void nombreDuplicadoDa409SinGuardar() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        when(groups.existsByName(org.getId(), "Equipo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ana.getId(), org.getId(), " Equipo "))
                .isInstanceOf(GroupNameTakenException.class);

        verifyNothingSaved();
    }

    @Test
    void sinPermisoNoHaceNada() {
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.MANAGE_GROUPS))
                .thenThrow(new PermissionDeniedException(OrgPermission.MANAGE_GROUPS));

        assertThatThrownBy(() -> service.create(ana.getId(), org.getId(), "Equipo"))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.delete(ana.getId(), org.getId(), UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(groups, groupMembers, events);
    }

    @Test
    void renombrarPublicaGroupRenamed() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        when(groups.saveAndFlush(group)).thenReturn(group);
        when(groupMembers.countByGroupId(group.getId())).thenReturn(3L);

        GroupSummary result = service.rename(ana.getId(), org.getId(), group.getId(), " Equipo  A ");

        assertThat(result).isEqualTo(new GroupSummary(group.getId(), "Equipo A", 3));
        assertThat(group.getUpdatedAt()).isEqualTo(NOW);
        verify(events).publishEvent(new GroupRenamed(org.getId(), anaActor, group.getId(), "Equipo", "Equipo A"));
    }

    @Test
    void renombrarSoloMayusculasCuentaComoCambio() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("equipo");
        when(groups.saveAndFlush(group)).thenReturn(group);

        service.rename(ana.getId(), org.getId(), group.getId(), "Equipo");

        verify(events).publishEvent(new GroupRenamed(org.getId(), anaActor, group.getId(), "equipo", "Equipo"));
    }

    @Test
    void renombrarSinCambioNoPublicaEvento() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");

        service.rename(ana.getId(), org.getId(), group.getId(), "  Equipo ");

        verify(events, never()).publishEvent(any());
        verify(groups, never()).saveAndFlush(any());
    }

    @Test
    void renombrarAUnNombreUsadoDa409() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        when(groups.existsByNameExcluding(org.getId(), "Otro", group.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.rename(ana.getId(), org.getId(), group.getId(), "Otro"))
                .isInstanceOf(GroupNameTakenException.class);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void grupoInexistenteODeOtraOrganizacionDa404() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdAndOrganizationId(unknown, org.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(ana.getId(), org.getId(), unknown, "Equipo"))
                .isInstanceOf(GroupNotFoundException.class);
        assertThatThrownBy(() -> service.delete(ana.getId(), org.getId(), unknown))
                .isInstanceOf(GroupNotFoundException.class);
        assertThatThrownBy(() -> service.addMember(ana.getId(), org.getId(), unknown, bea.getId()))
                .isInstanceOf(GroupNotFoundException.class);
        assertThatThrownBy(() -> service.removeMember(ana.getId(), org.getId(), unknown, bea.getId()))
                .isInstanceOf(GroupNotFoundException.class);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void borrarPublicaUnSoloEventoConElNumeroDeMiembros() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        when(groupMembers.countByGroupId(group.getId())).thenReturn(4L);

        service.delete(ana.getId(), org.getId(), group.getId());

        verify(groups).delete(group);
        verify(events).publishEvent(new GroupDeleted(org.getId(), anaActor, group.getId(), "Equipo", 4));
    }

    @Test
    void anadirMiembroPublicaGroupMemberAdded() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        givenOrgMember(bea, OrgRole.MEMBER);
        when(groupMembers.existsById(new MemberGroupMemberId(group.getId(), bea.getId()))).thenReturn(false);
        when(groupMembers.saveAndFlush(any())).thenAnswer(returnsFirstArg());

        GroupMember result = service.addMember(ana.getId(), org.getId(), group.getId(), bea.getId());

        assertThat(result).isEqualTo(new GroupMember(bea.getId(), "Bea", "bea", OrgRole.MEMBER, NOW));
        verify(events).publishEvent(new GroupMemberAdded(org.getId(), anaActor, group.getId(), "Equipo",
                bea.getId(), "Bea"));
    }

    @Test
    void anadirAQuienNoEsMiembroDeLaOrganizacionDa400() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        when(memberships.findByOrganizationIdAndUserId(org.getId(), bea.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMember(ana.getId(), org.getId(), group.getId(), bea.getId()))
                .isInstanceOf(NotOrganizationMemberException.class);
        assertThatThrownBy(() -> service.addMember(ana.getId(), org.getId(), group.getId(), null))
                .isInstanceOf(NotOrganizationMemberException.class);

        verify(groupMembers, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void anadirAQuienYaEstaDa409() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        givenOrgMember(bea, OrgRole.MEMBER);
        when(groupMembers.existsById(new MemberGroupMemberId(group.getId(), bea.getId()))).thenReturn(true);

        assertThatThrownBy(() -> service.addMember(ana.getId(), org.getId(), group.getId(), bea.getId()))
                .isInstanceOf(AlreadyInGroupException.class);

        verify(groupMembers, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void quitarMiembroPublicaGroupMemberRemoved() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        MemberGroupMember row = MemberGroupMember.of(group, bea.getId(), NOW);
        when(groupMembers.findById(new MemberGroupMemberId(group.getId(), bea.getId())))
                .thenReturn(Optional.of(row));
        when(users.findById(bea.getId())).thenReturn(Optional.of(bea));

        service.removeMember(ana.getId(), org.getId(), group.getId(), bea.getId());

        verify(groupMembers).delete(row);
        verify(events).publishEvent(new GroupMemberRemoved(org.getId(), anaActor, group.getId(), "Equipo",
                bea.getId(), "Bea"));
    }

    @Test
    void quitarAQuienNoEstaDa404() {
        givenActor(OrgPermission.MANAGE_GROUPS);
        MemberGroup group = givenGroup("Equipo");
        when(groupMembers.findById(new MemberGroupMemberId(group.getId(), bea.getId())))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(ana.getId(), org.getId(), group.getId(), bea.getId()))
                .isInstanceOf(GroupMemberNotFoundException.class);

        verify(groupMembers, never()).delete(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void listaYDetalleSoloPidenVerGrupos() {
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.VIEW_GROUPS))
                .thenReturn(Membership.member(org, ana, OrgRole.MEMBER));
        List<GroupSummary> summaries = List.of(new GroupSummary(UUID.randomUUID(), "Equipo", 2));
        when(groups.findSummaries(org.getId())).thenReturn(summaries);
        UUID unknown = UUID.randomUUID();
        when(groups.findByIdAndOrganizationId(unknown, org.getId())).thenReturn(Optional.empty());

        assertThat(service.list(ana.getId(), org.getId())).isEqualTo(summaries);
        assertThatThrownBy(() -> service.get(ana.getId(), org.getId(), unknown))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void detalleOrdenaLosMiembrosPorNombre() {
        when(permissions.require(ana.getId(), org.getId(), OrgPermission.VIEW_GROUPS))
                .thenReturn(Membership.member(org, ana, OrgRole.MEMBER));
        MemberGroup group = givenGroup("Equipo");
        AppUser zoe = user("zoe");
        when(groupMembers.findByGroupId(group.getId())).thenReturn(List.of(
                MemberGroupMember.of(group, zoe.getId(), NOW), MemberGroupMember.of(group, bea.getId(), NOW)));
        when(users.findAllById(any())).thenReturn(List.of(zoe, bea));
        when(memberships.findAllById(any())).thenReturn(List.of(Membership.member(org, zoe, OrgRole.ADMIN),
                Membership.member(org, bea, OrgRole.MEMBER)));

        GroupDetail detail = service.get(ana.getId(), org.getId(), group.getId());

        assertThat(detail.members()).extracting(GroupMember::displayName).containsExactly("Bea", "zoe");
        assertThat(detail.members()).extracting(GroupMember::role).containsExactly(OrgRole.MEMBER, OrgRole.ADMIN);
    }

    private void givenActor(OrgPermission permission) {
        when(permissions.require(ana.getId(), org.getId(), permission))
                .thenReturn(Membership.owner(org));
    }

    private MemberGroup givenGroup(String name) {
        MemberGroup group = withId(MemberGroup.create(org.getId(), name, NOW.minusSeconds(3600)), UUID.randomUUID());
        when(groups.findByIdAndOrganizationId(group.getId(), org.getId())).thenReturn(Optional.of(group));
        return group;
    }

    private void givenOrgMember(AppUser user, OrgRole role) {
        when(memberships.findByOrganizationIdAndUserId(org.getId(), user.getId()))
                .thenReturn(Optional.of(Membership.member(org, user, role)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void verifyNothingSaved() {
        verify(groups, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    private static AppUser user(String name) {
        return withId(AppUser.withPassword(name + "@example.com", "hash", name.toLowerCase(), name), UUID.randomUUID());
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
