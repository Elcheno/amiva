package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Un miembro actual de un grupo. Quitarlo borra la fila: el histórico vive en activity
@Entity
@Table(name = "member_group_member")
@IdClass(MemberGroupMemberId.class)
public class MemberGroupMember {

    @Id
    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected MemberGroupMember() {
    }

    public static MemberGroupMember of(MemberGroup group, UUID userId, Instant now) {
        MemberGroupMember member = new MemberGroupMember();
        member.groupId = group.getId();
        member.userId = userId;
        member.organizationId = group.getOrganizationId();
        member.addedAt = now;
        return member;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
