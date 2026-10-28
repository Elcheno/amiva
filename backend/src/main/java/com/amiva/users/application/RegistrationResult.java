package com.amiva.users.application;

import java.util.UUID;

public record RegistrationResult(UUID userId, String email, String username, String displayName, UUID orgId, String orgName) {
}
