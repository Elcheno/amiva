package com.amiva.users.api;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.application.OrganizationMember;
import com.amiva.users.domain.OrgRole;

public record MemberResponse(UUID userId, String username, String displayName, String email, OrgRole role,
        Instant createdAt) {

    static MemberResponse from(OrganizationMember member) {
        return new MemberResponse(member.userId(), member.username(), member.displayName(), member.email(),
                member.role(), member.createdAt());
    }
}
