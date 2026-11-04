package com.amiva.users.application;

public class AlreadyInGroupException extends RuntimeException {

    public AlreadyInGroupException() {
        super("Ese usuario ya está en el grupo");
    }
}
