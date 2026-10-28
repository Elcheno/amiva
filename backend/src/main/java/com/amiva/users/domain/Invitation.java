package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

// Invitación a una organización para un usuario registrado. Solo se guarda el hash del token;
// el token en claro va únicamente en el email
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "invitee_user_id", nullable = false, updatable = false)
    private UUID inviteeUserId;

    // NULL si el usuario que invitó ya no existe
    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // NULL mientras está pendiente
    @Column(name = "responded_at")
    private Instant respondedAt;

    protected Invitation() {
    }

    // Invitación nueva pendiente: solo ADMIN o MEMBER
    public static Invitation pending(UUID organizationId, UUID inviteeUserId, UUID invitedByUserId, OrgRole role,
            String tokenHash, Instant now) {
        requireAssignable(role);
        Invitation invitation = new Invitation();
        invitation.organizationId = organizationId;
        invitation.inviteeUserId = inviteeUserId;
        invitation.invitedByUserId = invitedByUserId;
        invitation.role = role;
        invitation.status = InvitationStatus.PENDING;
        invitation.tokenHash = tokenHash;
        invitation.createdAt = now;
        invitation.updatedAt = now;
        return invitation;
    }

    // Reenvío de una pendiente: token, rol e invitador nuevos. El enlace anterior deja de valer
    public void reissue(UUID invitedByUserId, OrgRole role, String tokenHash, Instant now) {
        requireAssignable(role);
        this.invitedByUserId = invitedByUserId;
        this.role = role;
        this.tokenHash = tokenHash;
        this.updatedAt = now;
    }

    public void accept(Instant now) {
        respond(InvitationStatus.ACCEPTED, now);
    }

    public void decline(Instant now) {
        respond(InvitationStatus.DECLINED, now);
    }

    // Solo se responde una vez y solo a una pendiente
    private void respond(InvitationStatus response, Instant now) {
        if (!isPending()) {
            throw new InvitationNotPendingException();
        }
        this.status = response;
        this.respondedAt = now;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    private static void requireAssignable(OrgRole role) {
        if (role == null || role == OrgRole.OWNER) {
            throw new RoleNotAssignableException();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getInviteeUserId() {
        return inviteeUserId;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public OrgRole getRole() {
        return role;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
