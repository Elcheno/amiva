package com.amiva.users.application;

// Datos ya validados por la capa web. timezone puede ser null
public record RegisterCommand(String email, String password, String displayName, String timezone) {
}
