package com.amiva.users.application;

import java.util.UUID;

import com.amiva.users.domain.OrgRole;

// Organización vista desde un miembro: su rol en ella y si es la suya propia
public record MyOrganization(UUID orgId, String name, OrgRole role, boolean own) {
}
