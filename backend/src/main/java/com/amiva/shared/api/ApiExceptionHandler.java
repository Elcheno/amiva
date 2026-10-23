package com.amiva.shared.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Errores de Spring MVC como ProblemDetail (RFC 9457) en toda la API
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // 400 con "errors": { campo: mensaje } para que el cliente marque cada campo
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBody().setDetail("La petición tiene campos no válidos");
        ex.getBody().setProperty("errors", errors);
        return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
    }
}
