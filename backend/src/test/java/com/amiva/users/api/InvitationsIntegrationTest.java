package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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

// Invitaciones en la organización de Ana con sesión real, base core del compose y emails capturados en memoria.
// Bea (ADMIN) y Carlos (MEMBER) se añaden con SQL; Dani y Elena aún no son miembros.
// Todos llevan la misma marca aleatoria en el nombre para buscarlos sin depender de otros usuarios de la base
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingMailSender.Config.class)
class InvitationsIntegrationTest {

    private static final String PASSWORD = "secreta123";
    private static final Pattern LINK = Pattern.compile("(\\S*/invite/)([A-Za-z0-9_-]+)");

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
    private RegistrationResult bea;
    private RegistrationResult carlos;
    private RegistrationResult dani;
    private RegistrationResult elena;

    @BeforeEach
    void setUp() {
        ana = register("Ana");
        bea = register("Bea");
        carlos = register("Carlos");
        dani = register("Dani");
        elena = register("Elena");
        addMember(ana.orgId(), bea.userId(), "ADMIN");
        addMember(ana.orgId(), carlos.userId(), "MEMBER");
        // El registro envía el email de verificación: no cuenta en estos tests
        mailSender.reset();
    }

    @AfterEach
    void cleanUp() {
        mailSender.reset();
        TestCleanup.deleteUsers(jdbc, emails);
    }

    @Test
    void adminInvitaComoMember201() throws Exception {
        mvc.perform(invite(ana.orgId(), dani.userId(), "MEMBER").session(login(bea)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").exists())
                .andExpect(jsonPath("$.userId").value(dani.userId().toString()))
                .andExpect(jsonPath("$.username").value(dani.username()))
                .andExpect(jsonPath("$.displayName").value(dani.displayName()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.invitedByName").value(bea.displayName()))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        List<MimeMessage> messages = mailSender.sentTo(dani.email());
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getSubject()).isEqualTo("Te han invitado a " + ana.orgName() + " en Amiva");
        String text = CapturingMailSender.plainText(messages.getFirst());
        assertThat(text).contains(bea.displayName()).contains("como miembro");
        Matcher link = LINK.matcher(text);
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).isEqualTo("http://localhost:5173/invite/");
        String token = link.group(2);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM invitation WHERE organization_id = ?", ana.orgId());
        assertThat(row).containsEntry("invitee_user_id", dani.userId())
                .containsEntry("invited_by_user_id", bea.userId())
                .containsEntry("role", "MEMBER")
                .containsEntry("status", "PENDING")
                .containsEntry("token_hash", sha256(token));
        assertThat(row.get("token_hash")).isNotEqualTo(token);

        Map<String, Object> activity = jdbc.queryForMap("""
                SELECT actor_id, target_type, target_id, details ->> 'memberName' AS member_name,
                       details ->> 'role' AS role
                FROM activity WHERE organization_id = ? AND action = 'MEMBER_INVITED'
                """, ana.orgId());
        assertThat(activity).containsEntry("actor_id", bea.userId())
                .containsEntry("target_type", "MEMBER")
                .containsEntry("target_id", dani.userId())
                .containsEntry("member_name", dani.displayName())
                .containsEntry("role", "MEMBER");
    }

    @Test
    void ownerInvitaComoAdmin() throws Exception {
        mvc.perform(invite(ana.orgId(), dani.userId(), "admin").session(login(ana)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(CapturingMailSender.plainText(mailSender.sentTo(dani.email()).getFirst()))
                .contains("como administrador");
    }

    @Test
    void memberNoPuedeInvitar403() throws Exception {
        mvc.perform(invite(ana.orgId(), dani.userId(), "MEMBER").session(login(carlos)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.permission").value("INVITE_MEMBERS"));

        assertNothingDone();
    }

    @Test
    void adminNoPuedeInvitarComoAdmin403() throws Exception {
        mvc.perform(invite(ana.orgId(), dani.userId(), "ADMIN").session(login(bea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("MANAGE_ROLES"));

        assertNothingDone();
    }

    @Test
    void yaMiembro409() throws Exception {
        MockHttpSession session = login(ana);

        mvc.perform(invite(ana.orgId(), carlos.userId(), "MEMBER").session(session))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(invite(ana.orgId(), ana.userId(), "MEMBER").session(session))
                .andExpect(status().isConflict());

        assertNothingDone();
    }

    @Test
    void usuarioInexistente404() throws Exception {
        mvc.perform(invite(ana.orgId(), UUID.randomUUID(), "MEMBER").session(login(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Usuario no encontrado"));

        assertNothingDone();
    }

    @Test
    void rolOwnerOInvalido400() throws Exception {
        MockHttpSession session = login(ana);

        mvc.perform(invite(ana.orgId(), dani.userId(), "OWNER").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(invite(ana.orgId(), dani.userId(), "JEFE").session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(post(invitations(ana.orgId())).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"MEMBER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.userId").exists());

        assertNothingDone();
    }

    @Test
    void reenviarReutilizaLaInvitacionPendiente() throws Exception {
        MockHttpSession session = login(ana);

        mvc.perform(invite(ana.orgId(), dani.userId(), "MEMBER").session(session)).andExpect(status().isCreated());
        mvc.perform(invite(ana.orgId(), dani.userId(), "ADMIN").session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        List<MimeMessage> messages = mailSender.sentTo(dani.email());
        assertThat(messages).hasSize(2);
        Matcher link = LINK.matcher(CapturingMailSender.plainText(messages.getLast()));
        assertThat(link.find()).isTrue();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role, token_hash FROM invitation WHERE organization_id = ? AND status = 'PENDING'",
                ana.orgId());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("role", "ADMIN").containsEntry("token_hash", sha256(link.group(2)));
        assertThat(invitesLogged()).isEqualTo(2);
    }

    @Test
    void listaLasPendientesDeLaOrganizacion() throws Exception {
        mvc.perform(invite(ana.orgId(), dani.userId(), "MEMBER").session(login(bea))).andExpect(status().isCreated());
        mvc.perform(invite(ana.orgId(), elena.userId(), "ADMIN").session(login(ana))).andExpect(status().isCreated());

        mvc.perform(get(invitations(ana.orgId())).session(login(bea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(elena.userId().toString()))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].invitedByName").value(ana.displayName()))
                .andExpect(jsonPath("$[1].userId").value(dani.userId().toString()))
                .andExpect(jsonPath("$[1].username").value(dani.username()))
                .andExpect(jsonPath("$[1].invitedByName").value(bea.displayName()))
                .andExpect(jsonPath("$[1].email").doesNotExist());

        mvc.perform(get(invitations(ana.orgId())).session(login(carlos)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("INVITE_MEMBERS"));
    }

    @Test
    void buscarExcluyeMiembrosNoDevuelveEmailYMarcaPendientes() throws Exception {
        MockHttpSession session = login(ana);
        mvc.perform(invite(ana.orgId(), elena.userId(), "MEMBER").session(session)).andExpect(status().isCreated());

        mvc.perform(search(ana.orgId(), "  " + tag.toUpperCase() + " ").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(dani.userId().toString()))
                .andExpect(jsonPath("$[0].username").value(dani.username()))
                .andExpect(jsonPath("$[0].displayName").value(dani.displayName()))
                .andExpect(jsonPath("$[0].pendingInvitation").value(false))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[1].userId").value(elena.userId().toString()))
                .andExpect(jsonPath("$[1].pendingInvitation").value(true));
    }

    @Test
    void buscarPorInicioDelUsername() throws Exception {
        mvc.perform(search(ana.orgId(), dani.username()).session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(dani.userId().toString()));
    }

    @Test
    void buscarConMenosDeTresCaracteresDevuelveVacio() throws Exception {
        mvc.perform(search(ana.orgId(), " zq ").session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // Sin escapar, '%%%' casaría con cualquier nombre
    @Test
    void buscarEscapaLosComodines() throws Exception {
        mvc.perform(search(ana.orgId(), "%%%").session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void memberNoPuedeBuscar403() throws Exception {
        mvc.perform(search(ana.orgId(), tag).session(login(carlos)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("INVITE_MEMBERS"));
    }

    @Test
    void noMiembro404() throws Exception {
        MockHttpSession session = login(dani);

        mvc.perform(search(ana.orgId(), tag).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get(invitations(ana.orgId())).session(session)).andExpect(status().isNotFound());
        mvc.perform(invite(ana.orgId(), elena.userId(), "MEMBER").session(session))
                .andExpect(status().isNotFound());

        assertNothingDone();
    }

    // El email va dentro de la transacción: sin email no queda invitación ni actividad
    @Test
    void siFallaElSmtp503YNoSeGuardaNada() throws Exception {
        MockHttpSession session = login(ana);
        mailSender.setFailing(true);

        mvc.perform(invite(ana.orgId(), dani.userId(), "MEMBER").session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No se pudo enviar el email de invitación"));

        assertThat(pendingInvitations()).isZero();
        assertThat(invitesLogged()).isZero();
    }

    @Test
    void sinCsrf403() throws Exception {
        mvc.perform(post(invitations(ana.orgId())).session(login(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"%s\", \"role\": \"MEMBER\"}".formatted(dani.userId())))
                .andExpect(status().isForbidden());

        assertNothingDone();
    }

    private void assertNothingDone() {
        assertThat(pendingInvitations()).isZero();
        assertThat(invitesLogged()).isZero();
        assertThat(mailSender.sentTo(dani.email())).isEmpty();
        assertThat(mailSender.sentTo(carlos.email())).isEmpty();
        assertThat(mailSender.sentTo(ana.email())).isEmpty();
    }

    private Integer pendingInvitations() {
        return jdbc.queryForObject("SELECT count(*) FROM invitation WHERE organization_id = ?", Integer.class,
                ana.orgId());
    }

    private Integer invitesLogged() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM activity WHERE organization_id = ? AND action = 'MEMBER_INVITED'",
                Integer.class, ana.orgId());
    }

    private RegistrationResult register(String name) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return registrationService.register(new RegisterCommand(email, PASSWORD, name + " " + tag, null));
    }

    private void addMember(UUID orgId, UUID userId, String role) {
        jdbc.update("INSERT INTO membership (organization_id, user_id, role) VALUES (?, ?, ?)", orgId, userId, role);
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

    private static String sha256(String token) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static String invitations(UUID orgId) {
        return "/api/orgs/" + orgId + "/invitations";
    }

    private static MockHttpServletRequestBuilder invite(UUID orgId, UUID userId, String role) {
        return post(invitations(orgId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"%s\", \"role\": \"%s\"}".formatted(userId, role))
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder search(UUID orgId, String q) {
        return get("/api/orgs/" + orgId + "/invitable-users").param("q", q);
    }
}
