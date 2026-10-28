package com.amiva.users.api;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.application.PendingInvitation;
import com.amiva.users.domain.OrgRole;

// invitedByName: null si el usuario que invitó ya no existe
public record InvitationResponse(UUID invitationId, UUID userId, String username, String displayName, OrgRole role,
        String invitedByName, Instant createdAt, Instant updatedAt) {

    static InvitationResponse from(PendingInvitation invitation) {
        return new InvitationResponse(invitation.invitationId(), invitation.userId(), invitation.username(),
                invitation.displayName(), invitation.role(), invitation.invitedByName(), invitation.createdAt(),
                invitation.updatedAt());
    }
}
