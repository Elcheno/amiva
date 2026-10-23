package com.amiva.users.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByOwnerId(UUID ownerId);
}
