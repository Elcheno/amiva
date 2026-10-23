package com.amiva.users.api;

import java.util.UUID;

import com.amiva.users.application.RegistrationResult;

public record RegisterResponse(UUID userId, String email, String displayName, UUID orgId, String orgName) {

    static RegisterResponse from(RegistrationResult result) {
        return new RegisterResponse(result.userId(), result.email(), result.displayName(), result.orgId(),
                result.orgName());
    }
}
