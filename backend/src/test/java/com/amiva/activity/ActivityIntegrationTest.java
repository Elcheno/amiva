package com.amiva.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amiva.TestCleanup;
import com.amiva.shared.domain.Actor;
import com.amiva.users.application.MembershipService;
import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationResult;
import com.amiva.users.application.RegistrationService;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.OrganizationCreated;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

// Registro de actividad contra la base core del compose: eventos reales, JSONB y triggers de inmutabilidad
@SpringBootTest
class ActivityIntegrationTest {

    @Autowired
    RegistrationService registrationService;

    @Autowired
    MembershipService membershipService;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> emails = new ArrayList<>();
    private RegistrationResult ana;

    @BeforeEach
    void setUp() {
        ana = register("Ana");
    }

    @AfterEach
    void cleanUp() {
        TestCleanup.deleteUsers(jdbc, emails);
    }

    @Test
    void registrarseGuardaOrgCreada() {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM activity WHERE organization_id = ?", ana.orgId());

        assertThat(row.get("action")).isEqualTo("ORG_CREATED");
        assertThat(row.get("actor_id")).isEqualTo(ana.userId());
        assertThat(row.get("actor_name")).isEqualTo("Ana");
        assertThat(row.get("anonymous")).isEqualTo(false);
        assertThat(row.get("target_type")).isEqualTo("ORGANIZATION");
        assertThat(row.get("target_id")).isEqualTo(ana.orgId());
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT details ->> 'organizationName' FROM activity WHERE organization_id = ?",
                String.class, ana.orgId())).isEqualTo("Org de Ana");
    }

    @Test
    void cambiarRolGuardaAutorMiembroYRoles() {
        RegistrationResult carlos = register("carlos");
        jdbc.update("INSERT INTO membership (organization_id, user_id, role) VALUES (?, ?, 'MEMBER')", ana.orgId(),
                carlos.userId());

        membershipService.changeRole(ana.userId(), ana.orgId(), carlos.userId(), OrgRole.ADMIN);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT actor_id, actor_name, target_type, target_id, details ->> 'memberName' AS member_name,
                       details ->> 'from' AS "from", details ->> 'to' AS "to"
                FROM activity WHERE organization_id = ? AND action = 'MEMBER_ROLE_CHANGED'
                """, ana.orgId());
        assertThat(row).containsEntry("actor_id", ana.userId())
                .containsEntry("actor_name", "Ana")
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", carlos.userId())
                .containsEntry("member_name", "carlos")
                .containsEntry("from", "MEMBER")
                .containsEntry("to", "ADMIN");
    }

    @Test
    void noSePuedeEditar() {
        assertThatThrownBy(() -> jdbc.update("UPDATE activity SET actor_name = 'Otro' WHERE organization_id = ?",
                ana.orgId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutable");
    }

    @Test
    void noSePuedeBorrar() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM activity WHERE organization_id = ?", ana.orgId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutable");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE activity"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("inmutable");
    }

    // Borrar la organización tampoco se lleva su actividad por delante
    @Test
    void noSePuedeBorrarLaOrganizacionConActividad() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM organization WHERE id = ?", ana.orgId()))
                .isInstanceOf(DataAccessException.class);
    }

    // El listener exige la transacción del caso de uso: un evento suelto no deja registro a medias
    @Test
    void eventoFueraDeTransaccionFalla() {
        assertThatThrownBy(() -> events.publishEvent(
                new OrganizationCreated(ana.orgId(), "Org", new Actor(ana.userId(), "Ana", false))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private RegistrationResult register(String displayName) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return registrationService.register(new RegisterCommand(email, "secreta123", displayName, null));
    }
}
