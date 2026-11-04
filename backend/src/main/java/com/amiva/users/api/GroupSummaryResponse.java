package com.amiva.users.api;

import java.util.UUID;

import com.amiva.users.application.GroupSummary;

public record GroupSummaryResponse(UUID id, String name, long memberCount) {

    static GroupSummaryResponse from(GroupSummary group) {
        return new GroupSummaryResponse(group.id(), group.name(), group.memberCount());
    }
}
