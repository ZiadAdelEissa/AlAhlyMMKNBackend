package com.mmkn.backend.inquiry;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    public record ErrorBody(String code, String message) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handle(ApiException error) {
        return ResponseEntity.status(error.status()).body(new ErrorBody(error.code(), error.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> malformed() {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", "Send a valid JSON request body."));
    }
}
