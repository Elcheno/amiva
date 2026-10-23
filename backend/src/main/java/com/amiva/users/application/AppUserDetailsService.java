package com.amiva.users.application;

import com.amiva.users.infrastructure.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Login con email: se normaliza igual que en el registro (sin espacios, sin distinguir mayúsculas)
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email.strip())
                // Usuarios solo OAuth (sin hash) no pueden entrar con contraseña
                .filter(user -> user.getPasswordHash() != null)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
}
