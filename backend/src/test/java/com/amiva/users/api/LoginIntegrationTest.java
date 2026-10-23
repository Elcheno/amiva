package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationResult;
import com.amiva.users.application.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Flujo completo de sesión contra la base core del compose: hash BCrypt real y cadena de seguridad real
@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    private static final String PASSWORD = "secreta123";
    private static final String INVALID_CREDENTIALS = "Email o contraseña incorrectos";

    @Autowired
    MockMvc mvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbc;

    private final String email = "it-" + UUID.randomUUID() + "@example.com";
    private RegistrationResult registered;

    @BeforeEach
    void setUp() {
        registered = registrationService.register(new RegisterCommand(email, PASSWORD, "Ana", "Europe/Madrid"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM organization WHERE owner_id IN (SELECT id FROM app_user WHERE lower(email) = lower(?))",
                email);
        jdbc.update("DELETE FROM app_user WHERE lower(email) = lower(?)", email);
    }

    @Test
    void loginCreaSesionYMeResponde() throws Exception {
        // Email con espacios y mayúsculas: se normaliza igual que en el registro
        MockHttpSession session = (MockHttpSession) mvc.perform(login(" " + email.toUpperCase() + " ", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(registered.userId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.orgId").value(registered.orgId().toString()))
                .andExpect(jsonPath("$.orgName").value("Org de Ana"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getRequest().getSession(false);

        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(registered.userId().toString()))
                .andExpect(jsonPath("$.orgName").value("Org de Ana"));
    }

    @Test
    void loginCambiaElIdDeSesion() throws Exception {
        MockHttpSession previous = new MockHttpSession();
        String previousId = previous.getId();

        MockHttpSession session = (MockHttpSession) mvc.perform(login(email, PASSWORD).session(previous))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(session.getId()).isNotEqualTo(previousId);
    }

    @Test
    void passwordIncorrecta401Generico() throws Exception {
        mvc.perform(login(email, "otra-clave"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
    }

    @Test
    void emailInexistenteMismo401() throws Exception {
        mvc.perform(login("nadie-" + UUID.randomUUID() + "@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(INVALID_CREDENTIALS));
    }

    @Test
    void meSinSesion401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void logoutInvalidaLaSesion() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginSinTokenCsrf403() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(email, password))
                .with(csrf());
    }

    private static String body(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
