package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    // NULL en usuarios solo OAuth
    @Column(name = "password_hash")
    private String passwordHash;

    // En minúsculas y único sin distinguir mayúsculas (ver Username)
    @Column(nullable = false, length = Username.MAX_LENGTH)
    private String username;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "platform_role", nullable = false, length = 20)
    private String platformRole;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    // Usuario nuevo registrado con email y contraseña, pendiente de verificar el email
    public static AppUser withPassword(String email, String passwordHash, String username, String displayName) {
        AppUser user = new AppUser();
        user.email = email;
        user.username = username;
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.emailVerified = false;
        user.platformRole = "user";
        user.enabled = true;
        return user;
    }

    public void verifyEmail() {
        emailVerified = true;
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

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPlatformRole() {
        return platformRole;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSuperadmin() {
        return "superadmin".equals(platformRole);
    }

    // Como actor de una acción (actividad, eventos)
    public Actor asActor() {
        return new Actor(id, displayName, isSuperadmin());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
