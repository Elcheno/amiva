package com.amiva.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;

import com.amiva.users.infrastructure.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM organization WHERE owner_id IN (SELECT id FROM app_user WHERE lower(email) = lower(?))",
                email);
        jdbc.update("DELETE FROM app_user WHERE lower(email) = lower(?)", email);
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
}
