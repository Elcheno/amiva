package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.MembershipService;
import com.amiva.users.domain.OrgRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orgs/{orgId}/members")
public class OrganizationMembersController {

    private final MembershipService membershipService;

    public OrganizationMembersController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId) {
        return membershipService.listMembers(principal.getUserId(), orgId).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PatchMapping("/{userId}")
    public MemberResponse changeRole(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @PathVariable UUID userId, @RequestBody ChangeRoleRequest request) {
        OrgRole role = OrgRole.assignable(request.role());
        return MemberResponse.from(membershipService.changeRole(principal.getUserId(), orgId, userId, role));
    }
}
