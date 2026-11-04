package com.amiva.users.application;

public class GroupNameTakenException extends RuntimeException {

    public GroupNameTakenException() {
        super("Ya hay un grupo con ese nombre en la organización");
    }
}
