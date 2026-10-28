package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;

import com.amiva.TestCleanup;
import com.amiva.users.domain.Username;
import com.amiva.users.infrastructure.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Contra la base core del compose: comprueba el guardado real y el índice único lower(email)
@SpringBootTest
class RegistrationServiceIntegrationTest {

    @Autowired
    RegistrationService service;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean
    AppUserRepository users;

    private final String email = "it-" + UUID.randomUUID() + "@example.com";
    private final String otherEmail = "it-" + UUID.randomUUID() + "@example.com";

    @AfterEach
    void cleanUp() {
        TestCleanup.deleteUsers(jdbc, email, otherEmail);
    }

    @Test
    void guardaUsuarioYOrganizacion() {
        RegistrationResult result = service.register(new RegisterCommand(email, "secreta123", "Ana", "Europe/Madrid"));

        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class,
                result.userId());
        assertThat(hash).startsWith("$2").isNotEqualTo("secreta123");
        assertThat(jdbc.queryForObject("SELECT timezone FROM organization WHERE id = ? AND owner_id = ?",
                String.class, result.orgId(), result.userId())).isEqualTo("Europe/Madrid");
    }

    @Test
    void creaMembershipOwnerDeSuOrganizacion() {
        RegistrationResult result = service.register(new RegisterCommand(email, "secreta123", "Ana", null));

        assertThat(jdbc.queryForList("SELECT role FROM membership WHERE organization_id = ? AND user_id = ?",
                String.class, result.orgId(), result.userId())).containsExactly("OWNER");
    }

    // V3 hace backfill y el registro crea la fila: cada organización tiene exactamente su dueño como OWNER
    @Test
    void cadaOrganizacionTieneSuDuenoComoOwner() {
        service.register(new RegisterCommand(email, "secreta123", "Ana", null));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization o
                WHERE NOT EXISTS (SELECT 1 FROM membership m
                                  WHERE m.organization_id = o.id AND m.user_id = o.owner_id AND m.role = 'OWNER')
                   OR (SELECT count(*) FROM membership m WHERE m.organization_id = o.id AND m.role = 'OWNER') <> 1
                """, Integer.class)).isZero();
    }

    @Test
    void indiceUnicoSinMayusculasEnCarrera() {
        service.register(new RegisterCommand(email, "secreta123", "Ana", null));
        // Simula que otra petición pasó la comprobación previa a la vez
        doReturn(false).when(users).existsByEmailIgnoreCase(anyString());

        assertThatThrownBy(() -> service.register(
                new RegisterCommand(email.toUpperCase(), "secreta123", "Otra", null)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization o JOIN app_user u ON u.id = o.owner_id"
                + " WHERE lower(u.email) = lower(?)", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void guardaUnUsernameGeneradoValido() {
        RegistrationResult result = service.register(new RegisterCommand(email, "secreta123", "Ana", null));

        String stored = jdbc.queryForObject("SELECT username FROM app_user WHERE id = ?", String.class,
                result.userId());
        assertThat(stored).isEqualTo(result.username());
        assertThat(Username.isValid(stored)).as(stored).isTrue();
    }

    @Test
    void usernameUnicoSinDistinguirMayusculas() {
        RegistrationResult ana = service.register(new RegisterCommand(email, "secreta123", "Ana", null));
        RegistrationResult bea = service.register(new RegisterCommand(otherEmail, "secreta123", "Bea", null));

        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET username = ? WHERE id = ?", ana.username(),
                bea.userId()))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_app_user_username_lower");
        // En mayúsculas tampoco entra: ya lo para el CHECK del formato antes que el índice
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET username = ? WHERE id = ?",
                ana.username().toUpperCase(), bea.userId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class,
                "uq_app_user_username_lower")).contains("UNIQUE").contains("lower((username)");
    }
}
