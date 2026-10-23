package com.amiva.users.api;

import com.amiva.users.application.EmailAlreadyRegisteredException;
import com.amiva.users.application.InvalidCredentialsException;
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
}
