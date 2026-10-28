package com.amiva.users.api;

import java.util.UUID;

import com.amiva.users.application.CurrentUser;

// Mismo cuerpo en /me y en /login
public record CurrentUserResponse(UUID userId, String email, String displayName, UUID orgId, String orgName,
        boolean emailVerified) {

    static CurrentUserResponse from(CurrentUser user) {
        return new CurrentUserResponse(user.userId(), user.email(), user.displayName(), user.orgId(),
                user.orgName(), user.emailVerified());
    }
}
