package com.amiva.users.application;

// Falló el envío del email de invitación: la invitación no se guarda (rollback) y se puede reintentar
public class InvitationEmailException extends RuntimeException {

    public InvitationEmailException(Throwable cause) {
        super("No se pudo enviar el email de invitación", cause);
    }
}
