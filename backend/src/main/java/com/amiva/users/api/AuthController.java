package com.amiva.users.api;

import java.util.Map;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.CurrentUserService;
import com.amiva.users.application.InvalidCredentialsException;
import com.amiva.users.application.RegisterCommand;
import com.amiva.users.application.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final CurrentUserService currentUserService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    // Nuevo id de sesión al autenticar (session fixation)
    private final SessionAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();

    public AuthController(RegistrationService registrationService, CurrentUserService currentUserService,
            AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.currentUserService = currentUserService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    // Fuerza a emitir la cookie XSRF-TOKEN para que la SPA pueda hacer POST
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        var result = registrationService.register(
                new RegisterCommand(request.email(), request.password(), request.displayName(), request.timezone()));
        return RegisterResponse.from(result);
    }

    // Autentica y guarda el SecurityContext en la sesión HTTP (cookie JSESSIONID)
    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.email(), login.password()));
        } catch (AuthenticationException e) {
            // No se distingue email inexistente, contraseña incorrecta ni cuenta deshabilitada
            throw new InvalidCredentialsException();
        }

        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return me((AppUserDetails) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUserDetails principal) {
        return currentUserService.find(principal.getUserId())
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Usuario no disponible"));
    }
}
