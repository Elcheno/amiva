package com.amiva.users.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// Clave compuesta de MemberGroupMember (group_id, user_id)
public class MemberGroupMemberId implements Serializable {

    private UUID groupId;
    private UUID userId;

    protected MemberGroupMemberId() {
    }

    public MemberGroupMemberId(UUID groupId, UUID userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MemberGroupMemberId other
                && Objects.equals(groupId, other.groupId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
