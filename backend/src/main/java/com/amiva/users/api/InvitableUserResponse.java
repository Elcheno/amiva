package com.amiva.users.api;

import java.util.UUID;

import com.amiva.users.application.InvitableUser;

// Sin email a propósito
public record InvitableUserResponse(UUID userId, String username, String displayName, boolean pendingInvitation) {

    static InvitableUserResponse from(InvitableUser user) {
        return new InvitableUserResponse(user.userId(), user.username(), user.displayName(), user.pendingInvitation());
    }
}
