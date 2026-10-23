package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "organization")
public class Organization {

    public static final int NAME_MAX_LENGTH = 150;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    // Zona IANA (Europe/Madrid, UTC...)
    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    // Organización propia que se crea al registrarse
    public static Organization ownedBy(AppUser owner, String timezone) {
        String name = "Org de " + owner.getDisplayName();
        Organization org = new Organization();
        org.name = name.length() > NAME_MAX_LENGTH ? name.substring(0, NAME_MAX_LENGTH).strip() : name;
        org.timezone = timezone;
        org.ownerId = owner.getId();
        return org;
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

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
