package com.amiva.users.domain;

import java.util.UUID;

import com.amiva.shared.domain.Actor;

// Evento: se ha invitado a un usuario a la organización (también al reenviar la invitación)
public record MemberInvited(UUID organizationId, Actor actor, UUID inviteeUserId, String inviteeName, OrgRole role) {
}
