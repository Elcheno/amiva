package com.amiva.users.application;

import java.util.UUID;

// Resultado de la búsqueda de usuarios a invitar. Sin email: la búsqueda no debe servir para descubrirlos
public record InvitableUser(UUID userId, String username, String displayName, boolean pendingInvitation) {
}
