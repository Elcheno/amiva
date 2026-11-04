package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.GroupDetail;

public record GroupDetailResponse(UUID id, String name, List<GroupMemberResponse> members) {

    static GroupDetailResponse from(GroupDetail group) {
        return new GroupDetailResponse(group.id(), group.name(),
                group.members().stream().map(GroupMemberResponse::from).toList());
    }
}
