package com.amiva.users.application;

import com.amiva.users.domain.OrgPermission;

public class PermissionDeniedException extends RuntimeException {

    private final OrgPermission permission;

    public PermissionDeniedException(OrgPermission permission) {
        super("Tu rol en la organización no permite " + permission.action());
        this.permission = permission;
    }

    public OrgPermission getPermission() {
        return permission;
    }
}
