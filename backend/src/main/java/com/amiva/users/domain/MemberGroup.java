package com.amiva.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

// Grupo de miembros de una organización. Los miembros están en MemberGroupMember
@Entity
@Table(name = "member_group")
public class MemberGroup {

    public static final int NAME_MAX_LENGTH = 60;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberGroup() {
    }

    public static MemberGroup create(UUID organizationId, String name, Instant now) {
        MemberGroup group = new MemberGroup();
        group.organizationId = organizationId;
        group.name = normalizeName(name);
        group.createdAt = now;
        group.updatedAt = now;
        return group;
    }

    // Quita espacios de los extremos y colapsa los repetidos; 1 a 60 caracteres (no bytes). Si no, 400
    public static String normalizeName(String name) {
        if (name == null) {
            throw new InvalidGroupNameException();
        }
        String normalized = name.strip().replaceAll("\\s+", " ");
        int length = normalized.codePointCount(0, normalized.length());
        if (length == 0 || length > NAME_MAX_LENGTH) {
            throw new InvalidGroupNameException();
        }
        return normalized;
    }

    // Devuelve true si el nombre cambia (también si solo cambian las mayúsculas)
    public boolean rename(String newName, Instant now) {
        String normalized = normalizeName(newName);
        if (normalized.equals(name)) {
            return false;
        }
        name = normalized;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
