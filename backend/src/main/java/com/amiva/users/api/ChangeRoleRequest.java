package com.amiva.users.api;

// El rol llega como texto y se valida en OrgRole.assignable para devolver siempre el mismo 400
public record ChangeRoleRequest(String role) {
}
