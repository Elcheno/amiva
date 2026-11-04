package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.MemberGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orgs/{orgId}/groups")
public class MemberGroupsController {

    private final MemberGroupService groupService;

    public MemberGroupsController(MemberGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupSummaryResponse> list(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID orgId) {
        return groupService.list(principal.getUserId(), orgId).stream()
                .map(GroupSummaryResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupSummaryResponse create(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @RequestBody GroupNameRequest request) {
        return GroupSummaryResponse.from(groupService.create(principal.getUserId(), orgId, request.name()));
    }

    @GetMapping("/{groupId}")
    public GroupDetailResponse get(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @PathVariable UUID groupId) {
        return GroupDetailResponse.from(groupService.get(principal.getUserId(), orgId, groupId));
    }

    @PatchMapping("/{groupId}")
    public GroupSummaryResponse rename(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @PathVariable UUID groupId, @RequestBody GroupNameRequest request) {
        return GroupSummaryResponse.from(groupService.rename(principal.getUserId(), orgId, groupId, request.name()));
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @PathVariable UUID groupId) {
        groupService.delete(principal.getUserId(), orgId, groupId);
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupMemberResponse addMember(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID orgId, @PathVariable UUID groupId, @Valid @RequestBody AddGroupMemberRequest request) {
        return GroupMemberResponse.from(
                groupService.addMember(principal.getUserId(), orgId, groupId, request.userId()));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @PathVariable UUID groupId, @PathVariable UUID userId) {
        groupService.removeMember(principal.getUserId(), orgId, groupId, userId);
    }
}
