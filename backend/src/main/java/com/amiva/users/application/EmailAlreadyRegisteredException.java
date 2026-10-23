package com.amiva.users.application;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Ese email ya está registrado");
    }
}
