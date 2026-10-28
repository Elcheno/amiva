package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amiva.CapturingMailSender;
import com.amiva.TestCleanup;
import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationResult;
import com.amiva.users.application.RegistrationService;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Respuesta del invitado a una invitación de la organización de Ana, con sesión real y emails capturados.
// Ana (OWNER) invita por la API y el token sale del email, como en el flujo real.
// Dani y Elena tienen el email verificado; Fran no
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingMailSender.Config.class)
class ReceivedInvitationsIntegrationTest {

    private static final String PASSWORD = "secreta123";
    private static final Pattern LINK = Pattern.compile("/invite/([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CapturingMailSender mailSender;

    private final String tag = "zq" + UUID.randomUUID().toString().substring(0, 8);
    private final List<String> emails = new ArrayList<>();
    private RegistrationResult ana;
    private RegistrationResult dani;
    private RegistrationResult elena;
    private RegistrationResult fran;
    private MockHttpSession anaSession;

    @BeforeEach
    void setUp() throws Exception {
        ana = register("Ana");
        dani = register("Dani");
        elena = register("Elena");
        fran = register("Fran");
        verifyEmail(dani);
        verifyEmail(elena);
        mailSender.reset();
        anaSession = login(ana);
    }

    @AfterEach
    void cleanUp() {
        mailSender.reset();
        TestCleanup.deleteUsers(jdbc, emails);
    }

    @Test
    void aceptarHaceMiembroConElRolYRegistraActividad() throws Exception {
        String token = invite(dani, "ADMIN");
        MockHttpSession session = login(dani);

        String id = mvc.perform(resolve(token).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ana.orgId().toString()))
                .andExpect(jsonPath("$.organizationName").value(ana.orgName()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.invitedByName").value(ana.displayName()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(id, "$.id");

        mvc.perform(get("/api/me/invitations").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(invitationId));

        mvc.perform(accept(invitationId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(ana.orgId().toString()))
                .andExpect(jsonPath("$.name").value(ana.orgName()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.own").value(false));

        mvc.perform(get("/api/orgs/" + ana.orgId() + "/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '%s')].role".formatted(dani.userId())).value("ADMIN"));

        Map<String, Object> row = jdbc.queryForMap("SELECT status, responded_at FROM invitation WHERE id = ?::uuid",
                invitationId);
        assertThat(row).containsEntry("status", "ACCEPTED");
        assertThat(row.get("responded_at")).isNotNull();

        Map<String, Object> activity = jdbc.queryForMap("""
                SELECT actor_id, actor_name, target_type, target_id, details ->> 'role' AS role
                FROM activity WHERE organization_id = ? AND action = 'MEMBER_JOINED'
                """, ana.orgId());
        assertThat(activity).containsEntry("actor_id", dani.userId())
                .containsEntry("actor_name", dani.displayName())
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", dani.userId())
                .containsEntry("role", "ADMIN");

        mvc.perform(get("/api/me/invitations").session(session))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/orgs/" + ana.orgId() + "/invitations").session(anaSession))
                .andExpect(jsonPath("$.length()").value(0));

        // Ya respondida: el token y el id dejan de valer
        mvc.perform(resolve(token).session(session)).andExpect(status().isNotFound());
        mvc.perform(accept(invitationId).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void rechazarLaQuitaSinMembresiaNiActividadYSePuedeReinvitar() throws Exception {
        String token = invite(dani, "MEMBER");
        MockHttpSession session = login(dani);
        String invitationId = resolveId(token, session);

        mvc.perform(decline(invitationId).session(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/me/invitations").session(session))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/orgs/" + ana.orgId() + "/invitations").session(anaSession))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM membership WHERE organization_id = ? AND user_id = ?",
                Integer.class, ana.orgId(), dani.userId())).isZero();
        assertThat(joinsLogged()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class,
                invitationId)).isEqualTo("DECLINED");
        mvc.perform(decline(invitationId).session(session)).andExpect(status().isNotFound());

        // La rechazada no bloquea una invitación nueva (fila nueva, la anterior se conserva)
        String again = invite(dani, "MEMBER");
        mvc.perform(get("/api/me/invitations").session(session))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(resolveId(again, session)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invitation WHERE organization_id = ?", Integer.class,
                ana.orgId())).isEqualTo(2);
    }

    @Test
    void tokenAnteriorTrasReenvio404() throws Exception {
        String first = invite(dani, "MEMBER");
        String second = invite(dani, "ADMIN");
        MockHttpSession session = login(dani);

        mvc.perform(resolve(first).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Esta invitación no es para tu cuenta o ya no está disponible"));
        mvc.perform(resolve(second).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(resolve("no-existe").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void otroUsuario404() throws Exception {
        String token = invite(dani, "MEMBER");
        String invitationId = resolveId(token, login(dani));
        MockHttpSession session = login(elena);

        mvc.perform(resolve(token).session(session)).andExpect(status().isNotFound());
        mvc.perform(accept(invitationId).session(session)).andExpect(status().isNotFound());
        mvc.perform(decline(invitationId).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/invitations").session(session)).andExpect(jsonPath("$.length()").value(0));

        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class,
                invitationId)).isEqualTo("PENDING");
    }

    // La lista y resolve funcionan sin verificar (para mostrar la invitación y el aviso); responder no
    @Test
    void sinEmailVerificado403ConReason() throws Exception {
        String token = invite(fran, "MEMBER");
        MockHttpSession session = login(fran);
        String invitationId = resolveId(token, session);
        mvc.perform(get("/api/me/invitations").session(session)).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(accept(invitationId).session(session))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(decline(invitationId).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("EMAIL_NOT_VERIFIED"));

        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class,
                invitationId)).isEqualTo("PENDING");
    }

    // Añadido por otra vía con la invitación aún pendiente: 409 y la invitación se queda como estaba
    @Test
    void yaMiembro409SinCambios() throws Exception {
        String token = invite(dani, "ADMIN");
        MockHttpSession session = login(dani);
        String invitationId = resolveId(token, session);
        jdbc.update("INSERT INTO membership (organization_id, user_id, role) VALUES (?, ?, 'MEMBER')", ana.orgId(),
                dani.userId());

        mvc.perform(accept(invitationId).session(session)).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class,
                invitationId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT role FROM membership WHERE organization_id = ? AND user_id = ?",
                String.class, ana.orgId(), dani.userId())).isEqualTo("MEMBER");
        assertThat(joinsLogged()).isZero();
    }

    @Test
    void sinSesion401() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/me/invitations")).andExpect(status().isUnauthorized());
        mvc.perform(resolve("token")).andExpect(status().isUnauthorized());
        mvc.perform(accept(id.toString())).andExpect(status().isUnauthorized());
        mvc.perform(decline(id.toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void sinCsrf403() throws Exception {
        String token = invite(dani, "MEMBER");
        MockHttpSession session = login(dani);
        String invitationId = resolveId(token, session);

        mvc.perform(post("/api/invitations/" + invitationId + "/accept").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invitations/" + invitationId + "/decline").session(session))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT status FROM invitation WHERE id = ?::uuid", String.class,
                invitationId)).isEqualTo("PENDING");
    }

    // Ana invita por la API; devuelve el token del último email recibido
    private String invite(RegistrationResult invitee, String role) throws Exception {
        mvc.perform(post("/api/orgs/" + ana.orgId() + "/invitations").session(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"%s\", \"role\": \"%s\"}".formatted(invitee.userId(), role))
                        .with(csrf()))
                .andExpect(status().isCreated());
        List<MimeMessage> messages = mailSender.sentTo(invitee.email());
        Matcher link = LINK.matcher(CapturingMailSender.plainText(messages.getLast()));
        assertThat(link.find()).isTrue();
        return link.group(1);
    }

    private String resolveId(String token, MockHttpSession session) throws Exception {
        String body = mvc.perform(resolve(token).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Integer joinsLogged() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM activity WHERE organization_id = ? AND action = 'MEMBER_JOINED'",
                Integer.class, ana.orgId());
    }

    private RegistrationResult register(String name) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return registrationService.register(new RegisterCommand(email, PASSWORD, name + " " + tag, null));
    }

    private void verifyEmail(RegistrationResult user) {
        jdbc.update("UPDATE app_user SET email_verified = true WHERE id = ?", user.userId());
    }

    private MockHttpSession login(RegistrationResult user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(user.email(), PASSWORD))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private static MockHttpServletRequestBuilder resolve(String token) {
        return post("/api/invitations/resolve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token))
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder accept(String invitationId) {
        return post("/api/invitations/" + invitationId + "/accept").with(csrf());
    }

    private static MockHttpServletRequestBuilder decline(String invitationId) {
        return post("/api/invitations/" + invitationId + "/decline").with(csrf());
    }
}
