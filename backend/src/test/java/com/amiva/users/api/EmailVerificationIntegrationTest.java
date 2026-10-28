package com.amiva.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amiva.CapturingMailSender;
import com.amiva.TestCleanup;
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

// Registro, email (capturado en memoria, sin Mailpit) y verificación contra la base core del compose
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingMailSender.Config.class)
class EmailVerificationIntegrationTest {

    private static final String PASSWORD = "secreta123";
    private static final Pattern LINK = Pattern.compile("(\\S*/verify-email\\?token=)([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CapturingMailSender mailSender;

    private final String email = "it-" + UUID.randomUUID() + "@example.com";

    @BeforeEach
    void setUp() {
        mailSender.reset();
    }

    @AfterEach
    void cleanUp() {
        mailSender.reset();
        TestCleanup.deleteUsers(jdbc, email);
    }

    @Test
    void registroEnviaEnlaceYVerificarMarcaElEmail() throws Exception {
        mvc.perform(register()).andExpect(status().isCreated());

        List<MimeMessage> messages = mailSender.sentTo(email);
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getSubject()).isEqualTo("Confirma tu email en Amiva");
        Matcher link = LINK.matcher(CapturingMailSender.plainText(messages.getFirst()));
        assertThat(link.find()).isTrue();
        assertThat(link.group(1)).isEqualTo("http://localhost:5173/verify-email?token=");
        String token = link.group(2);

        mvc.perform(verify(token)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT email_verified FROM app_user WHERE lower(email) = lower(?)",
                Boolean.class, email)).isTrue();
        mvc.perform(get("/api/auth/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        mvc.perform(verify(token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("USED"));
    }

    @Test
    void sinVerificarElLoginFuncionaYMeLoIndica() throws Exception {
        mvc.perform(register()).andExpect(status().isCreated());

        mvc.perform(get("/api/auth/me").session(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void tokenInventado400Invalid() throws Exception {
        mvc.perform(verify("no-existe-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.reason").value("INVALID"));
    }

    @Test
    void verificarSinTokenCsrf403() throws Exception {
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void reenviarSinSesion401() throws Exception {
        mvc.perform(post("/api/auth/verify-email/resend").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void reenviarNadaMasRegistrarse429() throws Exception {
        mvc.perform(register()).andExpect(status().isCreated());

        mvc.perform(post("/api/auth/verify-email/resend").session(login()).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("Retry-After"));

        assertThat(mailSender.sentTo(email)).hasSize(1);
    }

    @Test
    void siFallaElEnvioElRegistroResponde201() throws Exception {
        mailSender.setFailing(true);

        mvc.perform(register()).andExpect(status().isCreated());

        assertThat(mailSender.sentTo(email)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(email) = lower(?)",
                Integer.class, email)).isEqualTo(1);
    }

    private MockHttpServletRequestBuilder register() {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).with(csrf()).content("""
                {"email": "%s", "password": "%s", "displayName": "Ana"}
                """.formatted(email, PASSWORD));
    }

    private static MockHttpServletRequestBuilder verify(String token) {
        return post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content("{\"token\": \"%s\"}".formatted(token));
    }

    private MockHttpSession login() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()).content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
