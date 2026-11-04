package com.amiva.users.api;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.application.GroupMember;
import com.amiva.users.domain.OrgRole;

public record GroupMemberResponse(UUID userId, String displayName, String username, OrgRole role, Instant addedAt) {

    static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.userId(), member.displayName(), member.username(), member.role(),
                member.addedAt());
    }
}
