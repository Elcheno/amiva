package com.amiva.users.domain;

import java.util.Locale;

// Rol dentro de una organización. El OWNER es único y no se transfiere ni se asigna
public enum OrgRole {
    OWNER,
    ADMIN,
    MEMBER;

    // Rol que se puede asignar a un miembro: ADMIN o MEMBER. Cualquier otro valor se rechaza
    public static OrgRole assignable(String value) {
        if (value == null) {
            throw new RoleNotAssignableException();
        }
        OrgRole role;
        try {
            role = valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RoleNotAssignableException();
        }
        if (role == OWNER) {
            throw new RoleNotAssignableException();
        }
        return role;
    }

    // Solo el OWNER da y quita ADMIN; así ningún admin puede quitárselo a otro
    public boolean canManageRoles() {
        return this == OWNER;
    }
}
