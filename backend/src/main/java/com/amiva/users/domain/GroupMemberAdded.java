package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: un miembro de la organización se ha añadido a un grupo
public record GroupMemberAdded(UUID organizationId, Actor actor, UUID groupId, String groupName,
        UUID memberUserId, String memberName) {
}
