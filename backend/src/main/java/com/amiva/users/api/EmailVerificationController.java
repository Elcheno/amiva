package com.amiva.users.api;

import com.amiva.users.application.AppUserDetails;
import com.amiva.users.application.EmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/verify-email")
public class EmailVerificationController {

    private final EmailVerificationService verifications;

    public EmailVerificationController(EmailVerificationService verifications) {
        this.verifications = verifications;
    }

    // Público: el enlace del email se puede abrir sin sesión (o con la de otro usuario)
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody VerifyEmailRequest request) {
        verifications.verify(request.token());
    }

    @PostMapping("/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@AuthenticationPrincipal AppUserDetails principal) {
        verifications.resend(principal.getUserId());
    }
}
