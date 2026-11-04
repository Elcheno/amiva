package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: se ha creado un grupo de miembros
public record GroupCreated(UUID organizationId, Actor actor, UUID groupId, String groupName) {
}
