package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: un miembro se ha quitado de un grupo
public record GroupMemberRemoved(UUID organizationId, Actor actor, UUID groupId, String groupName,
        UUID memberUserId, String memberName) {
}
