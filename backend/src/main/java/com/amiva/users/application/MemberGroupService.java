package com.amiva.users.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.GroupCreated;
import com.amiva.users.domain.GroupDeleted;
import com.amiva.users.domain.GroupMemberAdded;
import com.amiva.users.domain.GroupMemberRemoved;
import com.amiva.users.domain.GroupRenamed;
import com.amiva.users.domain.MemberGroup;
import com.amiva.users.domain.MemberGroupMember;
import com.amiva.users.domain.MemberGroupMemberId;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.MembershipId;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MemberGroupMemberRepository;
import com.amiva.users.infrastructure.MemberGroupRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Grupos de miembros de una organización. Solo guardan los miembros actuales; las altas y bajas quedan en activity.
// Una carrera contra uq_member_group_org_name o pk_member_group_member sale como DataIntegrityViolationException
// y la traduce UsersExceptionHandler a 409
@Service
public class MemberGroupService {

    private static final Comparator<GroupMember> MEMBER_ORDER = Comparator
            .comparing(GroupMember::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GroupMember::userId);

    private final MemberGroupRepository groups;
    private final MemberGroupMemberRepository groupMembers;
    private final MembershipRepository memberships;
    private final AppUserRepository users;
    private final OrgPermissionService permissions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MemberGroupService(MemberGroupRepository groups, MemberGroupMemberRepository groupMembers,
            MembershipRepository memberships, AppUserRepository users, OrgPermissionService permissions,
            ApplicationEventPublisher events, Clock clock) {
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.memberships = memberships;
        this.users = users;
        this.permissions = permissions;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> list(UUID actorId, UUID orgId) {
        permissions.require(actorId, orgId, OrgPermission.VIEW_GROUPS);
        return groups.findSummaries(orgId);
    }

    @Transactional
    public GroupSummary create(UUID actorId, UUID orgId, String name) {
        permissions.require(actorId, orgId, OrgPermission.MANAGE_GROUPS);

        MemberGroup group = MemberGroup.create(orgId, name, clock.instant());
        if (groups.existsByName(orgId, group.getName())) {
            throw new GroupNameTakenException();
        }
        // flush para que una carrera con otro grupo del mismo nombre salte aquí y no al hacer commit
        group = groups.saveAndFlush(group);

        events.publishEvent(new GroupCreated(orgId, actorOf(actorId), group.getId(), group.getName()));
        return new GroupSummary(group.getId(), group.getName(), 0);
    }

    @Transactional(readOnly = true)
    public GroupDetail get(UUID actorId, UUID orgId, UUID groupId) {
        permissions.require(actorId, orgId, OrgPermission.VIEW_GROUPS);
        MemberGroup group = findGroup(orgId, groupId);

        List<MemberGroupMember> rows = groupMembers.findByGroupId(groupId);
        Map<UUID, AppUser> usersById = users.findAllById(rows.stream().map(MemberGroupMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<UUID, Membership> membershipsById = memberships
                .findAllById(rows.stream().map(r -> new MembershipId(orgId, r.getUserId())).toList())
                .stream()
                .collect(Collectors.toMap(Membership::getUserId, Function.identity()));

        List<GroupMember> members = rows.stream()
                .filter(r -> usersById.containsKey(r.getUserId()) && membershipsById.containsKey(r.getUserId()))
                .map(r -> toMember(r, usersById.get(r.getUserId()), membershipsById.get(r.getUserId())))
                .sorted(MEMBER_ORDER)
                .toList();
        return new GroupDetail(group.getId(), group.getName(), members);
    }

    // Si el nombre no cambia no hay nada que registrar
    @Transactional
    public GroupSummary rename(UUID actorId, UUID orgId, UUID groupId, String name) {
        permissions.require(actorId, orgId, OrgPermission.MANAGE_GROUPS);
        MemberGroup group = findGroup(orgId, groupId);

        String previousName = group.getName();
        String newName = MemberGroup.normalizeName(name);
        if (groups.existsByNameExcluding(orgId, newName, groupId)) {
            throw new GroupNameTakenException();
        }
        boolean changed = group.rename(newName, clock.instant());
        if (changed) {
            // flush para que una carrera con otro grupo del mismo nombre salte aquí y no al hacer commit
            group = groups.saveAndFlush(group);
            events.publishEvent(new GroupRenamed(orgId, actorOf(actorId), groupId, previousName, group.getName()));
        }
        return new GroupSummary(group.getId(), group.getName(), groupMembers.countByGroupId(groupId));
    }

    // Se borra aunque tenga miembros: las respuestas son de los usuarios, no del grupo.
    // Las filas de miembros caen por cascada y no se registra una baja por cada una
    @Transactional
    public void delete(UUID actorId, UUID orgId, UUID groupId) {
        permissions.require(actorId, orgId, OrgPermission.MANAGE_GROUPS);
        MemberGroup group = findGroup(orgId, groupId);

        long memberCount = groupMembers.countByGroupId(groupId);
        groups.delete(group);
        groups.flush();

        events.publishEvent(new GroupDeleted(orgId, actorOf(actorId), groupId, group.getName(), memberCount));
    }

    @Transactional
    public GroupMember addMember(UUID actorId, UUID orgId, UUID groupId, UUID userId) {
        permissions.require(actorId, orgId, OrgPermission.MANAGE_GROUPS);
        MemberGroup group = findGroup(orgId, groupId);

        if (userId == null) {
            throw new NotOrganizationMemberException();
        }
        Membership membership = memberships.findByOrganizationIdAndUserId(orgId, userId)
                .orElseThrow(NotOrganizationMemberException::new);
        AppUser user = users.findById(userId).orElseThrow(NotOrganizationMemberException::new);
        if (groupMembers.existsById(new MemberGroupMemberId(groupId, userId))) {
            throw new AlreadyInGroupException();
        }
        // flush para que una carrera con otra alta del mismo usuario salte aquí y no al hacer commit
        MemberGroupMember row = groupMembers.saveAndFlush(MemberGroupMember.of(group, userId, clock.instant()));

        events.publishEvent(new GroupMemberAdded(orgId, actorOf(actorId), groupId, group.getName(), userId,
                user.getDisplayName()));
        return toMember(row, user, membership);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID orgId, UUID groupId, UUID userId) {
        permissions.require(actorId, orgId, OrgPermission.MANAGE_GROUPS);
        MemberGroup group = findGroup(orgId, groupId);

        MemberGroupMember row = groupMembers.findById(new MemberGroupMemberId(groupId, userId))
                .orElseThrow(GroupMemberNotFoundException::new);
        // La fila existe: el usuario existe (FK en cascada con membership y app_user)
        AppUser user = users.findById(userId).orElseThrow(GroupMemberNotFoundException::new);
        groupMembers.delete(row);
        groupMembers.flush();

        events.publishEvent(new GroupMemberRemoved(orgId, actorOf(actorId), groupId, group.getName(), userId,
                user.getDisplayName()));
    }

    private MemberGroup findGroup(UUID orgId, UUID groupId) {
        return groups.findByIdAndOrganizationId(groupId, orgId).orElseThrow(GroupNotFoundException::new);
    }

    // El actor ya pasó require: es miembro y existe
    private Actor actorOf(UUID actorId) {
        return users.findById(actorId).orElseThrow().asActor();
    }

    private static GroupMember toMember(MemberGroupMember row, AppUser user, Membership membership) {
        return new GroupMember(user.getId(), user.getDisplayName(), user.getUsername(), membership.getRole(),
                row.getAddedAt());
    }
}
