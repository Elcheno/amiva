package com.amiva.users.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.amiva.users.domain.AppUser;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Usuario autenticado que se guarda en la sesión. El hash se borra tras autenticar
public class AppUserDetails implements UserDetails, CredentialsContainer {

    private final UUID userId;
    private final String email;
    private String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    AppUserDetails(AppUser user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getPlatformRole().toUpperCase()));
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
