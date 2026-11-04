package com.amiva.users.domain;

// Acción que se comprueba dentro de una organización. Qué rol tiene cada una: OrgRole
public enum OrgPermission {
    VIEW_MEMBERS("ver los miembros"),
    MANAGE_ROLES("cambiar roles"),
    INVITE_MEMBERS("invitar miembros"),
    VIEW_GROUPS("ver los grupos"),
    MANAGE_GROUPS("gestionar los grupos");

    private final String action;

    OrgPermission(String action) {
        this.action = action;
    }

    // Texto de la acción para los mensajes de error ("Sin permiso para ...")
    public String action() {
        return action;
    }
}
