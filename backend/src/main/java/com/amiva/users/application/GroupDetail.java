package com.amiva.users.application;

import java.util.List;
import java.util.UUID;

public record GroupDetail(UUID id, String name, List<GroupMember> members) {
}
