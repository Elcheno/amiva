package com.amiva.shared.domain;

import java.util.UUID;

// Quién hace una acción. superadmin: se registra, pero se muestra anónimo
public record Actor(UUID userId, String displayName, boolean superadmin) {
}
