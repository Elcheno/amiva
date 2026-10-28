package com.amiva.users.application;

// No existe, no es para este usuario, ya no está pendiente o el token no vale (también tras un reenvío).
// No se distingue para no revelar invitaciones ajenas
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("Esta invitación no es para tu cuenta o ya no está disponible");
    }
}
