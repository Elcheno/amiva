package com.amiva.users.api;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AddGroupMemberRequest(@NotNull UUID userId) {
}
