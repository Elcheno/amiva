package com.amiva.users.application;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.domain.OrgRole;

public record OrganizationMember(UUID userId, String username, String displayName, String email, OrgRole role, Instant createdAt) {
}
