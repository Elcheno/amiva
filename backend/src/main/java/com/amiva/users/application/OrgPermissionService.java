package com.amiva.users.application;

import java.util.UUID;

import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgPermission;
import com.amiva.users.infrastructure.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Único punto de comprobación de permisos en una organización. Cada caso de uso lo llama al empezar
@Service
public class OrgPermissionService {

    private final MembershipRepository memberships;

    public OrgPermissionService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    // Devuelve la membresía del actor si su rol tiene el permiso.
    // Organización inexistente y actor no miembro dan el mismo 404; miembro sin permiso, 403
    @Transactional(readOnly = true)
    public Membership require(UUID actorId, UUID orgId, OrgPermission permission) {
        Membership actor = memberships.findByOrganizationIdAndUserId(orgId, actorId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (!actor.getRole().has(permission)) {
            throw new PermissionDeniedException(permission);
        }
        return actor;
    }
}
