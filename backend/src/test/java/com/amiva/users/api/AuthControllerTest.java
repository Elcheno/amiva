package com.amiva.users.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.amiva.config.SecurityConfig;
import com.amiva.users.application.CurrentUserService;
import com.amiva.users.application.EmailAlreadyRegisteredException;
import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationResult;
import com.amiva.users.application.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String VALID_BODY = """
            {"email": " Ana@Example.com ", "password": "secreta123", "displayName": "Ana", "timezone": "Europe/Madrid"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RegistrationService registrationService;

    @MockitoBean
    CurrentUserService currentUserService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @Test
    void registra201SinDevolverElHash() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(registrationService.register(
                new RegisterCommand("Ana@Example.com", "secreta123", "Ana", "Europe/Madrid")))
                .thenReturn(new RegistrationResult(userId, "Ana@Example.com", "Ana", orgId, "Org de Ana"));

        mvc.perform(register(VALID_BODY).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("Ana@Example.com"))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.orgName").value("Org de Ana"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void campos400ConErroresPorCampo() throws Exception {
        String body = """
                {"email": "no-es-email", "password": "corta", "displayName": "   "}
                """;

        mvc.perform(register(body).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());

        verifyNoInteractions(registrationService);
    }

    @Test
    void password400SiSuperaLos72Bytes() throws Exception {
        // 40 caracteres pero 80 bytes en UTF-8
        String body = """
                {"email": "ana@example.com", "password": "%s", "displayName": "Ana"}
                """.formatted("ñ".repeat(40));

        mvc.perform(register(body).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void duplicado409() throws Exception {
        when(registrationService.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mvc.perform(register(VALID_BODY).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Ese email ya está registrado"));
    }

    @Test
    void sinTokenCsrf403() throws Exception {
        mvc.perform(register(VALID_BODY)).andExpect(status().isForbidden());

        verifyNoInteractions(registrationService);
    }

    @Test
    void login400ConErroresPorCampo() throws Exception {
        String body = """
                {"email": "no-es-email", "password": ""}
                """;

        mvc.perform(login(body).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(userDetailsService);
    }

    @Test
    void loginSinTokenCsrf403() throws Exception {
        String body = """
                {"email": "ana@example.com", "password": "secreta123"}
                """;

        mvc.perform(login(body)).andExpect(status().isForbidden());

        verifyNoInteractions(userDetailsService);
    }

    @Test
    void meSinSesion401SinRedirigir() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void logoutSinTokenCsrf403() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder login(String body) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder register(String body) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
