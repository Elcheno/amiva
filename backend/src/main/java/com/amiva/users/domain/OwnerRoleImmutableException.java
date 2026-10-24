package com.amiva.users.domain;

public class OwnerRoleImmutableException extends RuntimeException {

    public OwnerRoleImmutableException() {
        super("El rol del propietario de la organización no se puede cambiar");
    }
}
