package com.amiva.users.application;

public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException() {
        super("Ese usuario no es miembro de la organización");
    }
}
