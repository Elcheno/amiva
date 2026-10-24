package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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

// Roles dentro de la organización de Ana, con sesión real contra la base core del compose.
// Aún no hay invitaciones: Bea (ADMIN) y Carlos (MEMBER) se añaden con SQL
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationMembersIntegrationTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    MockMvc mvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> emails = new ArrayList<>();
    private RegistrationResult ana;
    private RegistrationResult bea;
    private RegistrationResult carlos;
    private RegistrationResult outsider;

    @BeforeEach
    void setUp() {
        ana = register("Ana");
        bea = register("Bea");
        carlos = register("carlos");
        outsider = register("Dani");
        addMember(ana.orgId(), bea.userId(), "ADMIN");
        addMember(ana.orgId(), carlos.userId(), "MEMBER");
    }

    // membership se borra en cascada con la organización y el usuario
    @AfterEach
    void cleanUp() {
        for (String email : emails) {
            jdbc.update("DELETE FROM organization WHERE owner_id IN (SELECT id FROM app_user WHERE lower(email) = lower(?))",
                    email);
            jdbc.update("DELETE FROM app_user WHERE lower(email) = lower(?)", email);
        }
    }

    @Test
    void ownerListaMiembrosConElOwnerPrimero() throws Exception {
        mvc.perform(get(members(ana.orgId())).session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value(ana.userId().toString()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].email").value(ana.email()))
                .andExpect(jsonPath("$[0].displayName").value("Ana"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].displayName").value("Bea"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andExpect(jsonPath("$[2].displayName").value("carlos"))
                .andExpect(jsonPath("$[2].role").value("MEMBER"));
    }

    @Test
    void memberTambienPuedeListar() throws Exception {
        mvc.perform(get(members(ana.orgId())).session(login(carlos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void ownerPasaMemberAAdmin() throws Exception {
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "ADMIN").session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(carlos.userId().toString()))
                .andExpect(jsonPath("$.displayName").value("carlos"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(roleOf(ana.orgId(), carlos.userId())).isEqualTo("ADMIN");
    }

    @Test
    void ownerQuitaAdmin() throws Exception {
        mvc.perform(changeRole(ana.orgId(), bea.userId(), "MEMBER").session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        assertThat(roleOf(ana.orgId(), bea.userId())).isEqualTo("MEMBER");
    }

    @Test
    void adminNoPuedeQuitarAdminAOtro403() throws Exception {
        addMember(ana.orgId(), outsider.userId(), "ADMIN");

        mvc.perform(changeRole(ana.orgId(), outsider.userId(), "MEMBER").session(login(bea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Sin permiso para cambiar roles"))
                .andExpect(jsonPath("$.permission").value("MANAGE_ROLES"));

        assertThat(roleOf(ana.orgId(), outsider.userId())).isEqualTo("ADMIN");
    }

    @Test
    void adminNoPuedeDarAdmin403() throws Exception {
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "ADMIN").session(login(bea)))
                .andExpect(status().isForbidden());

        assertThat(roleOf(ana.orgId(), carlos.userId())).isEqualTo("MEMBER");
    }

    @Test
    void memberNoPuedeCambiarRoles403() throws Exception {
        mvc.perform(changeRole(ana.orgId(), bea.userId(), "MEMBER").session(login(carlos)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));

        assertThat(roleOf(ana.orgId(), bea.userId())).isEqualTo("ADMIN");
    }

    @Test
    void cambiarAlOwner409() throws Exception {
        mvc.perform(changeRole(ana.orgId(), ana.userId(), "MEMBER").session(login(ana)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("El propietario no se puede cambiar"));

        assertThat(roleOf(ana.orgId(), ana.userId())).isEqualTo("OWNER");
    }

    @Test
    void asignarOwner400() throws Exception {
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "OWNER").session(login(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        assertThat(roleOf(ana.orgId(), carlos.userId())).isEqualTo("MEMBER");
    }

    @Test
    void rolInvalido400() throws Exception {
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "JEFE").session(login(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void usuarioDeOtraOrganizacion404() throws Exception {
        MockHttpSession session = login(outsider);

        mvc.perform(get(members(ana.orgId())).session(session))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "ADMIN").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void organizacionInexistenteMismo404() throws Exception {
        mvc.perform(get(members(UUID.randomUUID())).session(login(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organización no encontrada"));
    }

    @Test
    void targetNoMiembro404() throws Exception {
        mvc.perform(changeRole(ana.orgId(), outsider.userId(), "ADMIN").session(login(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Miembro no encontrado"));
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get(members(ana.orgId())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(changeRole(ana.orgId(), carlos.userId(), "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patchSinCsrf403() throws Exception {
        mvc.perform(patch(members(ana.orgId()) + "/" + carlos.userId()).session(login(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isForbidden());

        assertThat(roleOf(ana.orgId(), carlos.userId())).isEqualTo("MEMBER");
    }

    private RegistrationResult register(String displayName) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return registrationService.register(new RegisterCommand(email, PASSWORD, displayName, null));
    }

    private void addMember(UUID orgId, UUID userId, String role) {
        jdbc.update("INSERT INTO membership (organization_id, user_id, role) VALUES (?, ?, ?)", orgId, userId, role);
    }

    private String roleOf(UUID orgId, UUID userId) {
        return jdbc.queryForObject("SELECT role FROM membership WHERE organization_id = ? AND user_id = ?",
                String.class, orgId, userId);
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

    private static String members(UUID orgId) {
        return "/api/orgs/" + orgId + "/members";
    }

    private static MockHttpServletRequestBuilder changeRole(UUID orgId, UUID userId, String role) {
        return patch(members(orgId) + "/" + userId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"%s\"}".formatted(role))
                .with(csrf());
    }
}
