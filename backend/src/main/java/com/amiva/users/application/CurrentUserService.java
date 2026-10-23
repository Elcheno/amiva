package com.amiva.users.application;

import java.util.Optional;
import java.util.UUID;

import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;

    public CurrentUserService(AppUserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    // Vacío si el usuario ya no existe o está deshabilitado
    @Transactional(readOnly = true)
    public Optional<CurrentUser> find(UUID userId) {
        return users.findById(userId)
                .filter(AppUser::isEnabled)
                .map(user -> {
                    Optional<Organization> org = organizations.findByOwnerId(user.getId());
                    return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(),
                            org.map(Organization::getId).orElse(null), org.map(Organization::getName).orElse(null));
                });
    }
}
