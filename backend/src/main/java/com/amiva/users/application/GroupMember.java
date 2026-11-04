package com.amiva.users.application;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.domain.OrgRole;

public record GroupMember(UUID userId, String displayName, String username, OrgRole role, Instant addedAt) {
}
