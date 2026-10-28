package com.amiva.users.application;

// La acción exige haber verificado el email
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verifica tu email para continuar");
    }
}
