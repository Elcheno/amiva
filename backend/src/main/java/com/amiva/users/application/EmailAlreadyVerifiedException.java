package com.amiva.users.application;

public class EmailAlreadyVerifiedException extends RuntimeException {

    public EmailAlreadyVerifiedException() {
        super("Tu email ya está verificado");
    }
}
