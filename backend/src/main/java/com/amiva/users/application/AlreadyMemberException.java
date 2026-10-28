package com.amiva.users.application;

// El usuario invitado ya es miembro de la organización (también al invitarse a uno mismo)
public class AlreadyMemberException extends RuntimeException {

    public AlreadyMemberException() {
        super("Ese usuario ya es miembro de la organización");
    }
}
