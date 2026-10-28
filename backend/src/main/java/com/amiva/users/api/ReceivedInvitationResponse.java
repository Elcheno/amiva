package com.amiva.users.api;

import java.time.Instant;
import java.util.UUID;

import com.amiva.users.application.ReceivedInvitation;
import com.amiva.users.domain.OrgRole;

// invitedByName: null si el usuario que invitó ya no existe
public record ReceivedInvitationResponse(UUID id, UUID organizationId, String organizationName, OrgRole role,
        String invitedByName, Instant createdAt, Instant updatedAt) {

    static ReceivedInvitationResponse from(ReceivedInvitation invitation) {
        return new ReceivedInvitationResponse(invitation.id(), invitation.organizationId(),
                invitation.organizationName(), invitation.role(), invitation.invitedByName(), invitation.createdAt(),
                invitation.updatedAt());
    }
}
