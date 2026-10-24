package com.amiva.activity.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.amiva.shared.domain.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

// Entrada del registro de actividad. Inmutable: Hibernate no genera UPDATE y la BD bloquea UPDATE/DELETE
@Entity
@Immutable
@Table(name = "activity")
public class Activity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    // Copia del nombre en el momento de la acción
    @Column(name = "actor_name", nullable = false, updatable = false, length = 150)
    private String actorName;

    @Column(nullable = false, updatable = false)
    private boolean anonymous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private ActivityAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 30)
    private ActivityTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected Activity() {
    }

    public static Activity of(UUID organizationId, Actor actor, ActivityAction action, ActivityTargetType targetType,
            UUID targetId, Map<String, ?> details) {
        Activity activity = new Activity();
        activity.organizationId = organizationId;
        activity.actorId = actor.userId();
        activity.actorName = actor.displayName();
        activity.anonymous = actor.superadmin();
        activity.action = action;
        activity.targetType = targetType;
        activity.targetId = targetId;
        activity.details = Map.copyOf(details);
        return activity;
    }

    @PrePersist
    void onCreate() {
        occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public ActivityAction getAction() {
        return action;
    }

    public ActivityTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
