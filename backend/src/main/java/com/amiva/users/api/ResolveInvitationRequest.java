package com.amiva.users.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Token del enlace del email (base64url de 32 bytes: 43 caracteres)
public record ResolveInvitationRequest(@NotBlank @Size(max = 100) String token) {

    public ResolveInvitationRequest {
        token = token == null ? null : token.strip();
    }
}
