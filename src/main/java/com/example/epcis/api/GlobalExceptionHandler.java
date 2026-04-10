package com.example.epcis.api;

import com.example.epcis.infrastructure.xml.EpcisParsingException;
import com.example.epcis.infrastructure.xml.EpcisValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EpcisValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(EpcisValidationException ex) {
        log.warn("Schema validation error: {}", ex.getMessage());
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(EpcisParsingException.class)
    public ResponseEntity<Map<String, String>> handleParsingException(EpcisParsingException ex) {
        log.warn("XML parsing error: {}", ex.getMessage());
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal error: " + ex.getMessage()));
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
