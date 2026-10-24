package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: el rol de un miembro ha cambiado de verdad (from != to)
public record MemberRoleChanged(UUID organizationId, Actor actor, UUID memberUserId, String memberName,
        OrgRole from, OrgRole to) {
}
