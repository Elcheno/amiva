package com.amiva.users.api;

// El nombre se normaliza y valida en MemberGroup.normalizeName para devolver siempre el mismo 400
public record GroupNameRequest(String name) {
}
