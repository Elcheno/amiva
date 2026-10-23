package com.amiva.users.application;

// Mismo mensaje si el email no existe o la contraseña no coincide
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email o contraseña incorrectos");
    }
}
