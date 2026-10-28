package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: un usuario ha aceptado una invitación y ya es miembro (actor: el propio invitado)
public record MemberJoined(UUID organizationId, Actor actor, OrgRole role) {
}
