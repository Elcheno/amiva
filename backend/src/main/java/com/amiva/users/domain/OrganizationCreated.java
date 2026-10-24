package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: se ha creado una organización (al registrarse)
public record OrganizationCreated(UUID organizationId, String organizationName, Actor actor) {
}
