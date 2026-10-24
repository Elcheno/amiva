package com.amiva.users.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.Membership;
import com.amiva.users.domain.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {

    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<Membership> findByOrganizationId(UUID organizationId);

    List<Membership> findByUserId(UUID userId);
}
