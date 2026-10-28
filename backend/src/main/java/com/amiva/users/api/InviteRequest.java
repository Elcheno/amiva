package com.amiva.users.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

// El rol llega como texto y se valida en OrgRole.assignable para devolver siempre el mismo 400
public record InviteRequest(@NotNull UUID userId, String role) {
}
