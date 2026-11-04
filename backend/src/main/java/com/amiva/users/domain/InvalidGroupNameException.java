package com.amiva.users.domain;

public class InvalidGroupNameException extends RuntimeException {

    public InvalidGroupNameException() {
        super("El nombre del grupo debe tener entre 1 y " + MemberGroup.NAME_MAX_LENGTH + " caracteres");
    }
}
