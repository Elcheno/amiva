package com.amiva.users.application;

public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException() {
        super("Ese grupo no existe en la organización");
    }
}
