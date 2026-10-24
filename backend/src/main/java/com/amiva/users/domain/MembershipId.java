package com.amiva.users.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// Clave compuesta de Membership (organization_id, user_id)
public class MembershipId implements Serializable {

    private UUID organizationId;
    private UUID userId;

    protected MembershipId() {
    }

    public MembershipId(UUID organizationId, UUID userId) {
        this.organizationId = organizationId;
        this.userId = userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MembershipId other
                && Objects.equals(organizationId, other.organizationId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, userId);
    }
}
