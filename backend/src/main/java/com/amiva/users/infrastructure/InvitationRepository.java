package com.amiva.users.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.Invitation;
import com.amiva.users.domain.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    // Como mucho una pendiente por usuario y organización (uq_invitation_pending)
    Optional<Invitation> findByOrganizationIdAndInviteeUserIdAndStatus(UUID organizationId, UUID inviteeUserId,
            InvitationStatus status);

    List<Invitation> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(UUID organizationId, InvitationStatus status);

    // De entre esos usuarios, los que ya tienen una invitación pendiente en la organización
    @Query("""
            select i.inviteeUserId from Invitation i
            where i.organizationId = :orgId and i.inviteeUserId in :userIds
              and i.status = com.amiva.users.domain.InvitationStatus.PENDING
            """)
    List<UUID> findPendingInviteeIds(@Param("orgId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds);

    // Invitaciones recibidas por un usuario, más recientes primero
    List<Invitation> findByInviteeUserIdAndStatusOrderByUpdatedAtDesc(UUID inviteeUserId, InvitationStatus status);

    Optional<Invitation> findByTokenHash(String tokenHash);

    // SELECT ... FOR UPDATE: dos respuestas simultáneas a la misma invitación se ejecutan una tras otra
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.id = :id")
    Optional<Invitation> findByIdForUpdate(@Param("id") UUID id);
}
