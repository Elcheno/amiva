package com.amiva.users.domain;

import java.util.UUID;

// Evento: se ha registrado un usuario con email y contraseña
public record UserRegistered(UUID userId, String email, String displayName) {
}
