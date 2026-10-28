package com.amiva.users.api;

import com.amiva.users.application.AlreadyMemberException;
import com.amiva.users.application.EmailAlreadyRegisteredException;
import com.amiva.users.application.EmailAlreadyVerifiedException;
import com.amiva.users.application.InvalidCredentialsException;
import com.amiva.users.application.InvalidVerificationTokenException;
import com.amiva.users.application.InvitationEmailException;
import com.amiva.users.application.MemberNotFoundException;
import com.amiva.users.application.OrganizationNotFoundException;
import com.amiva.users.application.PermissionDeniedException;
import com.amiva.users.application.UserNotFoundException;
import com.amiva.users.application.VerificationResendTooSoonException;
import com.amiva.users.domain.OwnerRoleImmutableException;
import com.amiva.users.domain.RoleNotAssignableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = UsersExceptionHandler.class)
class UsersExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail emailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Email ya registrado");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problem.setTitle("Credenciales no válidas");
        return problem;
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    ProblemDetail organizationNotFound(OrganizationNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Organización no encontrada");
        return problem;
    }

    @ExceptionHandler(MemberNotFoundException.class)
    ProblemDetail memberNotFound(MemberNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Miembro no encontrado");
        return problem;
    }

    // Incluye el permiso que falta para que el front pueda reaccionar sin parsear el texto
    @ExceptionHandler(PermissionDeniedException.class)
    ProblemDetail permissionDenied(PermissionDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
        problem.setTitle("Sin permiso para " + e.getPermission().action());
        problem.setProperty("permission", e.getPermission().name());
        return problem;
    }

    @ExceptionHandler(OwnerRoleImmutableException.class)
    ProblemDetail ownerRoleImmutable(OwnerRoleImmutableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("El propietario no se puede cambiar");
        return problem;
    }

    @ExceptionHandler(RoleNotAssignableException.class)
    ProblemDetail roleNotAssignable(RoleNotAssignableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Rol no válido");
        return problem;
    }

    // reason: INVALID, EXPIRED o USED, para que el front muestre el mensaje adecuado
    @ExceptionHandler(InvalidVerificationTokenException.class)
    ProblemDetail invalidVerificationToken(InvalidVerificationTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Enlace de verificación no válido");
        problem.setProperty("reason", e.getReason().name());
        return problem;
    }

    @ExceptionHandler(EmailAlreadyVerifiedException.class)
    ProblemDetail emailAlreadyVerified(EmailAlreadyVerifiedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Email ya verificado");
        return problem;
    }

    // Retry-After en segundos (redondeado hacia arriba)
    @ExceptionHandler(VerificationResendTooSoonException.class)
    ResponseEntity<ProblemDetail> verificationResendTooSoon(VerificationResendTooSoonException e) {
        long seconds = Math.max(1, (e.getRetryAfter().toMillis() + 999) / 1000);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        problem.setTitle("Demasiados reenvíos");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(problem);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail userNotFound(UserNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Usuario no encontrado");
        return problem;
    }

    @ExceptionHandler(AlreadyMemberException.class)
    ProblemDetail alreadyMember(AlreadyMemberException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Ya es miembro");
        return problem;
    }

    // SMTP caído o lento: no se ha guardado nada y se puede reintentar
    @ExceptionHandler(InvitationEmailException.class)
    ProblemDetail invitationEmail(InvitationEmailException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("Email no enviado");
        return problem;
    }
}
