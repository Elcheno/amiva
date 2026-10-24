package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "membership")
@IdClass(MembershipId.class)
public class Membership {

    @Id
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Membership() {
    }

    // El dueño de la organización (organization.owner_id) como OWNER
    public static Membership owner(Organization org) {
        Membership membership = new Membership();
        membership.organizationId = org.getId();
        membership.userId = org.getOwnerId();
        membership.role = OrgRole.OWNER;
        return membership;
    }

    // Miembro no propietario: solo ADMIN o MEMBER
    public static Membership member(Organization org, AppUser user, OrgRole role) {
        requireAssignable(role);
        Membership membership = new Membership();
        membership.organizationId = org.getId();
        membership.userId = user.getId();
        membership.role = role;
        return membership;
    }

    public void changeRole(OrgRole newRole) {
        if (role == OrgRole.OWNER) {
            throw new OwnerRoleImmutableException();
        }
        requireAssignable(newRole);
        role = newRole;
    }

    private static void requireAssignable(OrgRole role) {
        if (role == null || role == OrgRole.OWNER) {
            throw new RoleNotAssignableException();
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrgRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return role == OrgRole.OWNER;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
