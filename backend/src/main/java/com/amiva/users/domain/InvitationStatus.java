package com.amiva.users.domain;

// Estado de una invitación. Solo se responde a una PENDING; la fila se conserva tras aceptar o rechazar
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
