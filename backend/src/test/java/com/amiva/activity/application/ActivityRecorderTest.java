package com.amiva.activity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import com.amiva.activity.domain.Activity;
import com.amiva.activity.domain.ActivityAction;
import com.amiva.activity.domain.ActivityTargetType;
import com.amiva.activity.infrastructure.ActivityRepository;
import com.amiva.shared.domain.Actor;
import com.amiva.users.domain.MemberRoleChanged;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.OrganizationCreated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityRecorderTest {

    @Mock
    ActivityRepository activities;

    ActivityRecorder recorder;

    private final UUID orgId = UUID.randomUUID();
    private final Actor ana = new Actor(UUID.randomUUID(), "Ana", false);

    @BeforeEach
    void setUp() {
        recorder = new ActivityRecorder(activities);
    }

    @Test
    void organizacionCreada() {
        recorder.on(new OrganizationCreated(orgId, "Org de Ana", ana));

        Activity activity = saved();
        assertThat(activity.getOrganizationId()).isEqualTo(orgId);
        assertThat(activity.getActorId()).isEqualTo(ana.userId());
        assertThat(activity.getActorName()).isEqualTo("Ana");
        assertThat(activity.isAnonymous()).isFalse();
        assertThat(activity.getAction()).isEqualTo(ActivityAction.ORG_CREATED);
        assertThat(activity.getTargetType()).isEqualTo(ActivityTargetType.ORGANIZATION);
        assertThat(activity.getTargetId()).isEqualTo(orgId);
        assertThat(activity.getDetails()).isEqualTo(Map.of("organizationName", "Org de Ana"));
    }

    @Test
    void rolCambiadoGuardaNombreCopiadoYRoles() {
        UUID carlosId = UUID.randomUUID();

        recorder.on(new MemberRoleChanged(orgId, ana, carlosId, "carlos", OrgRole.MEMBER, OrgRole.ADMIN));

        Activity activity = saved();
        assertThat(activity.getAction()).isEqualTo(ActivityAction.MEMBER_ROLE_CHANGED);
        assertThat(activity.getTargetType()).isEqualTo(ActivityTargetType.MEMBER);
        assertThat(activity.getTargetId()).isEqualTo(carlosId);
        assertThat(activity.getDetails())
                .isEqualTo(Map.of("memberName", "carlos", "from", "MEMBER", "to", "ADMIN"));
    }

    // La identidad se guarda; ocultarla al mostrar es cosa de la vista de actividad
    @Test
    void superadminQuedaMarcadoComoAnonimo() {
        Actor superadmin = new Actor(UUID.randomUUID(), "Soporte", true);

        recorder.on(new OrganizationCreated(orgId, "Org", superadmin));

        Activity activity = saved();
        assertThat(activity.isAnonymous()).isTrue();
        assertThat(activity.getActorId()).isEqualTo(superadmin.userId());
    }

    private Activity saved() {
        ArgumentCaptor<Activity> activity = ArgumentCaptor.forClass(Activity.class);
        verify(activities).save(activity.capture());
        return activity.getValue();
    }
}
