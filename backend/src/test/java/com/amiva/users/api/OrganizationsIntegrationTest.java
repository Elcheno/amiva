package com.amiva.users.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.amiva.TestCleanup;
import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationResult;
import com.amiva.users.application.RegistrationService;
import com.amiva.users.domain.AppUser;
import com.amiva.users.domain.Membership;
import com.amiva.users.domain.OrgRole;
import com.amiva.users.domain.Organization;
import com.amiva.users.infrastructure.AppUserRepository;
import com.amiva.users.infrastructure.MembershipRepository;
import com.amiva.users.infrastructure.OrganizationRepository;
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

// Organizaciones del usuario: la propia y en las que participa, con sesión real contra la base core del compose
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationsIntegrationTest {

    private static final String PASSWORD = "secreta123";

    @Autowired
    MockMvc mvc;

    @Autowired
    RegistrationService registrationService;

    @Autowired
    AppUserRepository users;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> emails = new ArrayList<>();
    private RegistrationResult ana;
    private RegistrationResult bea;
    private RegistrationResult outsider;

    @BeforeEach
    void setUp() {
        ana = register("Ana");
        bea = register("Bea");
        outsider = register("Dani");
        // Aún no hay invitaciones: Ana participa como ADMIN en la organización de Bea
        addMember(bea.orgId(), ana.userId(), OrgRole.ADMIN);
    }

    // membership se borra en cascada con la organización y el usuario
    @AfterEach
    void cleanUp() {
        TestCleanup.deleteUsers(jdbc, emails);
    }

    @Test
    void listaLaPropiaPrimeroYLuegoDondeParticipa() throws Exception {
        mvc.perform(get("/api/orgs").session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orgId").value(ana.orgId().toString()))
                .andExpect(jsonPath("$[0].name").value("Org de Ana"))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].own").value(true))
                .andExpect(jsonPath("$[1].orgId").value(bea.orgId().toString()))
                .andExpect(jsonPath("$[1].name").value("Org de Bea"))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].own").value(false));
    }

    @Test
    void sinOtrasOrganizacionesSoloLaPropia() throws Exception {
        mvc.perform(get("/api/orgs").session(login(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orgId").value(outsider.orgId().toString()))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].own").value(true));
    }

    @Test
    void detalleDeLaPropia() throws Exception {
        mvc.perform(get("/api/orgs/" + ana.orgId()).session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(ana.orgId().toString()))
                .andExpect(jsonPath("$.name").value("Org de Ana"))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.own").value(true));
    }

    @Test
    void detalleDeUnaDondeParticipa() throws Exception {
        mvc.perform(get("/api/orgs/" + bea.orgId()).session(login(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(bea.orgId().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.own").value(false));
    }

    @Test
    void detalleDeUnaDondeNoEsMiembro404() throws Exception {
        mvc.perform(get("/api/orgs/" + bea.orgId()).session(login(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Organización no encontrada"));
    }

    @Test
    void detalleDeOrganizacionInexistente404() throws Exception {
        mvc.perform(get("/api/orgs/" + UUID.randomUUID()).session(login(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Organización no encontrada"));
    }

    @Test
    void sinSesion401() throws Exception {
        mvc.perform(get("/api/orgs"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/orgs/" + ana.orgId()))
                .andExpect(status().isUnauthorized());
    }

    private RegistrationResult register(String displayName) {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        emails.add(email);
        return registrationService.register(new RegisterCommand(email, PASSWORD, displayName, null));
    }

    private void addMember(UUID orgId, UUID userId, OrgRole role) {
        Organization org = organizations.findById(orgId).orElseThrow();
        AppUser user = users.findById(userId).orElseThrow();
        memberships.save(Membership.member(org, user, role));
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
}
