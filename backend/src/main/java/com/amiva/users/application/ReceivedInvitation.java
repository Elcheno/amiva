package com.amiva.users.application;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.domain.OrgRole;

// Invitación pendiente vista por el invitado. invitedByName: null si el usuario que invitó ya no existe
public record ReceivedInvitation(UUID id, UUID organizationId, String organizationName, OrgRole role,
        String invitedByName, Instant createdAt, Instant updatedAt) {
}
