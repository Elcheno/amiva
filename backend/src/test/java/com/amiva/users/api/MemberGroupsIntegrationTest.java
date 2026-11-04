package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amiva.TestCleanup;
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

// Grupos en la organización de Ana con sesión real y la base core del compose.
// Bea (ADMIN) y Carlos (MEMBER) se añaden con SQL; Dani no es miembro de la organización de Ana.
// Zoe es otra organización: sus grupos no se ven desde la de Ana
@SpringBootTest
@AutoConfigureMockMvc
class MemberGroupsIntegrationTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    MockMvc mvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbc;

    private final String tag = "zq" + UUID.randomUUID().toString().substring(0, 8);
    private final List<String> emails = new ArrayList<>();
    private RegistrationResult ana;
    private RegistrationResult bea;
    private RegistrationResult carlos;
    private RegistrationResult dani;
    private RegistrationResult zoe;

    @BeforeEach
    void setUp() {
        ana = register("Ana");
        bea = register("Bea");
        carlos = register("Carlos");
        dani = register("Dani");
        zoe = register("Zoe");
        addMember(ana.orgId(), bea.userId(), "ADMIN");
        addMember(ana.orgId(), carlos.userId(), "MEMBER");
    }

    // activity es inmutable y bloquea el borrado de la organización: TestCleanup la vacía antes.
    // Los grupos y sus miembros caen por cascada con la organización
    @AfterEach
    void cleanUp() {
        TestCleanup.deleteUsers(jdbc, emails);
    }

    @Test
    void ownerHaceElFlujoCompleto() throws Exception {
        MockHttpSession session = login(ana);

        String groupId = create(session, ana.orgId(), "  Equipo   A ");
        mvc.perform(get(groups(ana.orgId())).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(groupId))
                .andExpect(jsonPath("$[0].name").value("Equipo A"))
                .andExpect(jsonPath("$[0].memberCount").value(0));

        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(carlos.userId().toString()))
                .andExpect(jsonPath("$.displayName").value(carlos.displayName()))
                .andExpect(jsonPath("$.username").value(carlos.username()))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.addedAt").exists());
        mvc.perform(addMember(ana.orgId(), groupId, bea.userId()).session(session)).andExpect(status().isCreated());

        mvc.perform(get(groups(ana.orgId()) + "/" + groupId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(groupId))
                .andExpect(jsonPath("$.name").value("Equipo A"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(bea.userId().toString()))
                .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.members[1].userId").value(carlos.userId().toString()));

        mvc.perform(patch(groups(ana.orgId()) + "/" + groupId).contentType(MediaType.APPLICATION_JSON)
                        .content(name("Equipo B")).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Equipo B"))
                .andExpect(jsonPath("$.memberCount").value(2));

        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId + "/members/" + carlos.userId()).with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get(groups(ana.orgId())).session(session))
                .andExpect(jsonPath("$[0].memberCount").value(1));

        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId).with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get(groups(ana.orgId())).session(session)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get(groups(ana.orgId()) + "/" + groupId).session(session)).andExpect(status().isNotFound());

        assertThat(actions(ana.orgId())).containsExactly("GROUP_CREATED", "GROUP_MEMBER_ADDED", "GROUP_MEMBER_ADDED",
                "GROUP_RENAMED", "GROUP_MEMBER_REMOVED", "GROUP_DELETED");
    }

    @Test
    void adminTambienGestiona() throws Exception {
        MockHttpSession session = login(bea);

        String groupId = create(session, ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session)).andExpect(status().isCreated());
        mvc.perform(patch(groups(ana.orgId()) + "/" + groupId).contentType(MediaType.APPLICATION_JSON)
                        .content(name("Otro")).with(csrf()).session(session))
                .andExpect(status().isOk());
        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT DISTINCT actor_id FROM activity WHERE action LIKE 'GROUP_%'"
                + " AND organization_id = ?", UUID.class, ana.orgId())).isEqualTo(bea.userId());
    }

    @Test
    void memberVeLosGruposPeroNoGestiona() throws Exception {
        String groupId = create(login(ana), ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, bea.userId()).session(login(ana))).andExpect(status().isCreated());
        MockHttpSession session = login(carlos);
        int logged = groupActivity(ana.orgId());

        mvc.perform(get(groups(ana.orgId())).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberCount").value(1));
        mvc.perform(get(groups(ana.orgId()) + "/" + groupId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));

        mvc.perform(post(groups(ana.orgId())).contentType(MediaType.APPLICATION_JSON).content(name("Nuevo"))
                        .with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("MANAGE_GROUPS"));
        mvc.perform(patch(groups(ana.orgId()) + "/" + groupId).contentType(MediaType.APPLICATION_JSON)
                        .content(name("Otro")).with(csrf()).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("MANAGE_GROUPS"));
        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId).with(csrf()).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId + "/members/" + bea.userId()).with(csrf())
                        .session(session))
                .andExpect(status().isForbidden());

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_group_member WHERE group_id = ?", Integer.class,
                UUID.fromString(groupId))).isEqualTo(1);
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get(groups(ana.orgId()))).andExpect(status().isUnauthorized());
        mvc.perform(post(groups(ana.orgId())).contentType(MediaType.APPLICATION_JSON).content(name("Equipo"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noMiembroDeLaOrganizacion404() throws Exception {
        String groupId = create(login(ana), ana.orgId(), "Equipo");
        MockHttpSession session = login(dani);

        mvc.perform(get(groups(ana.orgId())).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(groups(ana.orgId()) + "/" + groupId).session(session)).andExpect(status().isNotFound());
        mvc.perform(post(groups(ana.orgId())).contentType(MediaType.APPLICATION_JSON).content(name("Otro"))
                        .with(csrf()).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId).with(csrf()).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get(groups(UUID.randomUUID())).session(session)).andExpect(status().isNotFound());

        assertThat(groupActivity(ana.orgId())).isEqualTo(1);
    }

    @Test
    void grupoDeOtraOrganizacion404() throws Exception {
        String zoeGroup = create(login(zoe), zoe.orgId(), "De Zoe");
        MockHttpSession session = login(ana);

        mvc.perform(get(groups(ana.orgId()) + "/" + zoeGroup).session(session)).andExpect(status().isNotFound());
        mvc.perform(patch(groups(ana.orgId()) + "/" + zoeGroup).contentType(MediaType.APPLICATION_JSON)
                        .content(name("Mío")).with(csrf()).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(addMember(ana.orgId(), zoeGroup, bea.userId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(delete(groups(ana.orgId()) + "/" + zoeGroup + "/members/" + bea.userId()).with(csrf())
                        .session(session))
                .andExpect(status().isNotFound());
        mvc.perform(delete(groups(ana.orgId()) + "/" + zoeGroup).with(csrf()).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get(groups(ana.orgId())).session(session)).andExpect(jsonPath("$.length()").value(0));

        assertThat(jdbc.queryForObject("SELECT name FROM member_group WHERE id = ?", String.class,
                UUID.fromString(zoeGroup))).isEqualTo("De Zoe");
    }

    @Test
    void nombreDuplicado409TambienConOtrasMayusculas() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        String other = create(session, ana.orgId(), "Otro");
        int logged = groupActivity(ana.orgId());

        for (String duplicate : List.of("Equipo", "equipo", "  EQUIPO ")) {
            mvc.perform(post(groups(ana.orgId())).contentType(MediaType.APPLICATION_JSON).content(name(duplicate))
                            .with(csrf()).session(session))
                    .andExpect(status().isConflict());
        }
        mvc.perform(patch(groups(ana.orgId()) + "/" + other).contentType(MediaType.APPLICATION_JSON)
                        .content(name("equipo")).with(csrf()).session(session))
                .andExpect(status().isConflict());

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged);
        // El mismo nombre en otra organización sí vale
        create(login(zoe), zoe.orgId(), "Equipo");
        assertThat(groupId).isNotBlank();
    }

    @Test
    void renombrarSoloMayusculasSeRegistra() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "equipo");

        mvc.perform(patch(groups(ana.orgId()) + "/" + groupId).contentType(MediaType.APPLICATION_JSON)
                        .content(name("Equipo")).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Equipo"));
        // Sin cambio: 200 y sin actividad nueva
        mvc.perform(patch(groups(ana.orgId()) + "/" + groupId).contentType(MediaType.APPLICATION_JSON)
                        .content(name(" Equipo ")).with(csrf()).session(session))
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT target_type, target_id, details ->> 'from' AS from_name, details ->> 'to' AS to_name
                FROM activity WHERE organization_id = ? AND action = 'GROUP_RENAMED'
                """, ana.orgId());
        assertThat(row).containsEntry("target_type", "GROUP")
                .containsEntry("target_id", UUID.fromString(groupId))
                .containsEntry("from_name", "equipo")
                .containsEntry("to_name", "Equipo");
    }

    @Test
    void nombreNoValido400() throws Exception {
        MockHttpSession session = login(ana);
        int logged = groupActivity(ana.orgId());

        for (String body : List.of(name(""), name("   "), name("x".repeat(61)), "{}", "{\"name\": null}")) {
            mvc.perform(post(groups(ana.orgId())).contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(csrf()).session(session))
                    .andExpect(status().isBadRequest());
        }
        create(session, ana.orgId(), "x".repeat(60));

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged + 1);
    }

    @Test
    void anadirAQuienNoEsMiembro400() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        int logged = groupActivity(ana.orgId());

        mvc.perform(addMember(ana.orgId(), groupId, dani.userId()).session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(addMember(ana.orgId(), groupId, UUID.randomUUID()).session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(post(groups(ana.orgId()) + "/" + groupId + "/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(csrf()).session(session))
                .andExpect(status().isBadRequest());

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_group_member WHERE group_id = ?", Integer.class,
                UUID.fromString(groupId))).isZero();
    }

    @Test
    void anadirDosVeces409() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, bea.userId()).session(session)).andExpect(status().isCreated());
        int logged = groupActivity(ana.orgId());

        mvc.perform(addMember(ana.orgId(), groupId, bea.userId()).session(session)).andExpect(status().isConflict());

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged);
    }

    @Test
    void quitarAQuienNoEsta404() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        int logged = groupActivity(ana.orgId());

        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId + "/members/" + bea.userId()).with(csrf())
                        .session(session))
                .andExpect(status().isNotFound());

        assertThat(groupActivity(ana.orgId())).isEqualTo(logged);
    }

    @Test
    void borrarConMiembrosDa204YBorraSusFilas() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, bea.userId()).session(session)).andExpect(status().isCreated());
        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session)).andExpect(status().isCreated());

        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_group WHERE id = ?", Integer.class,
                UUID.fromString(groupId))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_group_member WHERE group_id = ?", Integer.class,
                UUID.fromString(groupId))).isZero();
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT target_type, target_id, details ->> 'groupName' AS group_name,
                       (details ->> 'memberCount')::int AS member_count
                FROM activity WHERE organization_id = ? AND action = 'GROUP_DELETED'
                """, ana.orgId());
        assertThat(row).containsEntry("target_type", "GROUP")
                .containsEntry("target_id", UUID.fromString(groupId))
                .containsEntry("group_name", "Equipo")
                .containsEntry("member_count", 2);
        // No se emite una baja por cada miembro
        assertThat(actions(ana.orgId()).stream().filter("GROUP_MEMBER_REMOVED"::equals).count()).isZero();
    }

    @Test
    void laActividadGuardaAutorTargetYDetalles() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session)).andExpect(status().isCreated());
        mvc.perform(delete(groups(ana.orgId()) + "/" + groupId + "/members/" + carlos.userId()).with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        Map<String, Object> created = jdbc.queryForMap("""
                SELECT actor_id, target_type, target_id, details ->> 'groupName' AS group_name
                FROM activity WHERE organization_id = ? AND action = 'GROUP_CREATED'
                """, ana.orgId());
        assertThat(created).containsEntry("actor_id", ana.userId())
                .containsEntry("target_type", "GROUP")
                .containsEntry("target_id", UUID.fromString(groupId))
                .containsEntry("group_name", "Equipo");
        for (String action : List.of("GROUP_MEMBER_ADDED", "GROUP_MEMBER_REMOVED")) {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT actor_id, target_type, target_id, details ->> 'groupName' AS group_name,
                           details ->> 'memberUserId' AS member_user_id, details ->> 'memberName' AS member_name
                    FROM activity WHERE organization_id = ? AND action = ?
                    """, ana.orgId(), action);
            assertThat(row).as(action).containsEntry("actor_id", ana.userId())
                    .containsEntry("target_type", "GROUP")
                    .containsEntry("target_id", UUID.fromString(groupId))
                    .containsEntry("group_name", "Equipo")
                    .containsEntry("member_user_id", carlos.userId().toString())
                    .containsEntry("member_name", carlos.displayName());
        }
    }

    @Test
    void salirDeLaOrganizacionSacaDeLosGrupos() throws Exception {
        MockHttpSession session = login(ana);
        String groupId = create(session, ana.orgId(), "Equipo");
        mvc.perform(addMember(ana.orgId(), groupId, carlos.userId()).session(session)).andExpect(status().isCreated());

        jdbc.update("DELETE FROM membership WHERE organization_id = ? AND user_id = ?", ana.orgId(), carlos.userId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_group_member WHERE group_id = ?", Integer.class,
                UUID.fromString(groupId))).isZero();
    }

    private int groupActivity(UUID orgId) {
        return jdbc.queryForObject("SELECT count(*) FROM activity WHERE organization_id = ? AND target_type = 'GROUP'",
                Integer.class, orgId);
    }

    private List<String> actions(UUID orgId) {
        return jdbc.queryForList("""
                SELECT action FROM activity WHERE organization_id = ? AND target_type = 'GROUP' ORDER BY occurred_at, id
                """, String.class, orgId);
    }

    private String create(MockHttpSession session, UUID orgId, String name) throws Exception {
        String body = mvc.perform(post(groups(orgId)).contentType(MediaType.APPLICATION_JSON).content(name(name))
                        .with(csrf()).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(0))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
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

    private static String groups(UUID orgId) {
        return "/api/orgs/" + orgId + "/groups";
    }

    private static String name(String name) {
        return "{\"name\": \"%s\"}".formatted(name);
    }

    private static MockHttpServletRequestBuilder addMember(UUID orgId, String groupId, UUID userId) {
        return post(groups(orgId) + "/" + groupId + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": \"%s\"}".formatted(userId))
                .with(csrf());
    }
}
