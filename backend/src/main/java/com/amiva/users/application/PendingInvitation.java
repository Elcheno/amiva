package com.amiva.users.application;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.domain.OrgRole;

// invitedByName: null si el usuario que invitó ya no existe
public record PendingInvitation(UUID invitationId, UUID userId, String username, String displayName, OrgRole role,
        String invitedByName, Instant createdAt, Instant updatedAt) {
}
