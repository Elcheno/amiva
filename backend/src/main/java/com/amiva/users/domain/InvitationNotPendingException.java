package com.amiva.users.domain;

// Se intenta aceptar o rechazar una invitación que ya no está pendiente
public class InvitationNotPendingException extends RuntimeException {

    public InvitationNotPendingException() {
        super("La invitación ya no está pendiente");
    }
}
