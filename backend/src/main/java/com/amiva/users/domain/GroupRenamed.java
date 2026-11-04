package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: el nombre de un grupo ha cambiado de verdad (también si solo cambian las mayúsculas)
public record GroupRenamed(UUID organizationId, Actor actor, UUID groupId, String from, String to) {
}
