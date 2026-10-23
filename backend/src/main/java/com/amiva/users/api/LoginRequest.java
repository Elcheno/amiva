package com.amiva.users.api;

import com.amiva.shared.api.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// Sin mínimo de contraseña: solo se rechaza lo que BCrypt no puede comparar (más de 72 bytes)
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotEmpty @Size(max = 72) @MaxUtf8Bytes(72) String password) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }
}
