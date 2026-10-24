package com.amiva.users.application;

public class RoleChangeForbiddenException extends RuntimeException {

    public RoleChangeForbiddenException() {
        super("Solo el propietario puede cambiar roles");
    }
}
