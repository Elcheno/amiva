package com.amiva.users.application;

public class NotOrganizationMemberException extends RuntimeException {

    public NotOrganizationMemberException() {
        super("Ese usuario no es miembro de la organización");
    }
}
