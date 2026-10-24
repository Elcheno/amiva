package com.amiva.users.api;

import com.amiva.users.application.EmailAlreadyRegisteredException;
import com.amiva.users.application.InvalidCredentialsException;
import com.amiva.users.application.MemberNotFoundException;
import com.amiva.users.application.OrganizationNotFoundException;
import com.amiva.users.application.PermissionDeniedException;
import com.amiva.users.domain.OwnerRoleImmutableException;
import com.amiva.users.domain.RoleNotAssignableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
}
