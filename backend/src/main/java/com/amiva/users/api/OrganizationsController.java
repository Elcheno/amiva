package com.amiva.users.api;

import java.util.List;
import java.util.UUID;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.OrganizationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orgs")
public class OrganizationsController {

    private final OrganizationService organizationService;

    public OrganizationsController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationResponse> list(@AuthenticationPrincipal AppUserDetails principal) {
        return organizationService.listMine(principal.getUserId()).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @GetMapping("/{orgId}")
    public OrganizationResponse get(@AuthenticationPrincipal AppUserDetails principal, @PathVariable UUID orgId) {
        return OrganizationResponse.from(organizationService.findMine(principal.getUserId(), orgId));
    }
}
