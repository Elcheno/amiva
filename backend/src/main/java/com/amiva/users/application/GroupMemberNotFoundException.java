package com.amiva.users.application;

public class GroupMemberNotFoundException extends RuntimeException {

    public GroupMemberNotFoundException() {
        super("Ese usuario no está en el grupo");
    }
}
