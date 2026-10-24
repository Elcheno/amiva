package com.amiva.users.api;

import java.util.UUID;

import com.amiva.users.application.MyOrganization;
import com.amiva.users.domain.OrgRole;

public record OrganizationResponse(UUID orgId, String name, OrgRole role, boolean own) {

    static OrganizationResponse from(MyOrganization org) {
        return new OrganizationResponse(org.orgId(), org.name(), org.role(), org.own());
    }
}
