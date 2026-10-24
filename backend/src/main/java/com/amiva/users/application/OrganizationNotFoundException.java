package com.amiva.users.application;

// La organización no existe o el usuario no es miembro: no se distingue para no revelar que existe
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException() {
        super("Organización no encontrada");
    }
}
