package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.ReceivedInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Invitaciones recibidas por el usuario de la sesión
@RestController
public class ReceivedInvitationsController {

    private final ReceivedInvitationService receivedInvitations;

    public ReceivedInvitationsController(ReceivedInvitationService receivedInvitations) {
        this.receivedInvitations = receivedInvitations;
    }

    @GetMapping("/me/invitations")
    public List<ReceivedInvitationResponse> listMine(@AuthenticationPrincipal AppUserDetails principal) {
        return receivedInvitations.listMine(principal.getUserId()).stream()
                .map(ReceivedInvitationResponse::from)
                .toList();
    }

    // Token en el body (como verify-email): nunca en la URL de la API
    @PostMapping("/invitations/resolve")
    public ReceivedInvitationResponse resolve(@AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody ResolveInvitationRequest request) {
        return ReceivedInvitationResponse.from(receivedInvitations.resolve(principal.getUserId(), request.token()));
    }

    // Devuelve la organización a la que acaba de entrar
    @PostMapping("/invitations/{invitationId}/accept")
    public OrganizationResponse accept(@AuthenticationPrincipal AppUserDetails principal,
            @PathVariable UUID invitationId) {
        return OrganizationResponse.from(receivedInvitations.accept(principal.getUserId(), invitationId));
    }

    @PostMapping("/invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID invitationId) {
        receivedInvitations.decline(principal.getUserId(), invitationId);
    }
}
