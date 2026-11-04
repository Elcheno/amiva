package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: se ha borrado un grupo con memberCount miembros (no se emite una baja por cada uno)
public record GroupDeleted(UUID organizationId, Actor actor, UUID groupId, String groupName, long memberCount) {
}
