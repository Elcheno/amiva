package com.amiva.activity.application;

import java.util.Map;

import com.amiva.activity.domain.Activity;
import com.amiva.activity.domain.ActivityAction;
import com.amiva.activity.domain.ActivityTargetType;
import com.amiva.activity.infrastructure.ActivityRepository;
import com.amiva.users.domain.MemberInvited;
import com.amiva.users.domain.MemberRoleChanged;
import com.amiva.users.domain.OrganizationCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Convierte los eventos de dominio en entradas del registro.
// Síncrono y dentro de la transacción del caso de uso: si falla el registro, falla la acción
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ActivityRecorder {

    private final ActivityRepository activities;

    public ActivityRecorder(ActivityRepository activities) {
        this.activities = activities;
    }

    @EventListener
    public void on(OrganizationCreated event) {
        activities.save(Activity.of(event.organizationId(), event.actor(), ActivityAction.ORG_CREATED,
                ActivityTargetType.ORGANIZATION, event.organizationId(),
                Map.of("organizationName", event.organizationName())));
    }

    @EventListener
    public void on(MemberRoleChanged event) {
        activities.save(Activity.of(event.organizationId(), event.actor(), ActivityAction.MEMBER_ROLE_CHANGED,
                ActivityTargetType.MEMBER, event.memberUserId(),
                Map.of("memberName", event.memberName(), "from", event.from().name(), "to", event.to().name())));
    }

    // También al reenviar una invitación pendiente. target: el usuario invitado (aún no es miembro)
    @EventListener
    public void on(MemberInvited event) {
        activities.save(Activity.of(event.organizationId(), event.actor(), ActivityAction.MEMBER_INVITED,
                ActivityTargetType.MEMBER, event.inviteeUserId(),
                Map.of("memberName", event.inviteeName(), "role", event.role().name())));
    }
}
