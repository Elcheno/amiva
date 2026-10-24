package com.amiva.users.domain;

// Rol desconocido o que no se puede asignar (OWNER)
public class RoleNotAssignableException extends RuntimeException {

    public RoleNotAssignableException() {
        super("Solo se puede asignar ADMIN o MEMBER");
    }
}
