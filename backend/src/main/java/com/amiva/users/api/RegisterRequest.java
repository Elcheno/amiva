package com.amiva.users.api;

import com.amiva.shared.api.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
        @NotBlank @Size(max = 150) String displayName,
        String timezone) {

    // Se recortan antes de validar: los límites aplican al valor sin espacios a los extremos
    public RegisterRequest {
        email = email == null ? null : email.strip();
        displayName = displayName == null ? null : displayName.strip();
    }
}
