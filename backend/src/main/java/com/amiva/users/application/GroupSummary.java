package com.amiva.users.application;

import java.util.UUID;

public record GroupSummary(UUID id, String name, long memberCount) {
}
