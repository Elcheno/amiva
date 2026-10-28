package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.InvitationService;
import com.amiva.users.domain.OrgRole;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orgs/{orgId}")
public class InvitationsController {

    private final InvitationService invitationService;

    public InvitationsController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId,
            @Valid @RequestBody InviteRequest request) {
        OrgRole role = OrgRole.assignable(request.role());
        return InvitationResponse.from(
                invitationService.invite(principal.getUserId(), orgId, request.userId(), role));
    }

    @GetMapping("/invitations")
    public List<InvitationResponse> listPending(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID orgId) {
        return invitationService.listPending(principal.getUserId(), orgId).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    @GetMapping("/invitable-users")
    public List<InvitableUserResponse> searchInvitable(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID orgId, @RequestParam(defaultValue = "") String q) {
        return invitationService.searchInvitable(principal.getUserId(), orgId, q).stream()
                .map(InvitableUserResponse::from)
                .toList();
    }
}
