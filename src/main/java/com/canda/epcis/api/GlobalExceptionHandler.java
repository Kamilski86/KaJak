package com.canda.epcis.api;

import com.canda.epcis.application.inventory.EpcNotFoundException;
import com.canda.epcis.application.query.EpcisQueryException;
import com.canda.epcis.domain.service.CbvValidationException;
import com.canda.epcis.domain.service.Epcis2SchemaValidationException;
import com.canda.epcis.infrastructure.xml.EpcisParsingException;
import com.canda.epcis.infrastructure.xml.EpcisValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;

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

    @ExceptionHandler(CbvValidationException.class)
    public ResponseEntity<Map<String, String>> handleCbvValidationException(CbvValidationException ex) {
        log.warn("CBV vocabulary validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Epcis2SchemaValidationException.class)
    public ResponseEntity<Map<String, String>> handleSchemaValidationException(Epcis2SchemaValidationException ex) {
        String ref = UUID.randomUUID().toString();
        log.error("Rendered output failed GS1 EPCIS 2.0 schema validation [ref={}]: {}", ref, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Output schema validation failed. Reference: " + ref));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return badRequest(ex.getHeaderName() + " header is required");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(EpcNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEpcNotFoundException(EpcNotFoundException ex) {
        log.warn("EPC not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EpcisQueryException.class)
    public ResponseEntity<Map<String, String>> handleQueryNotFound(EpcisQueryException ex) {
        log.warn("Query not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        // Never leak internal details to the caller. Log with a reference ID for traceability.
        String ref = UUID.randomUUID().toString();
        log.error("Unexpected error [ref={}]: {}", ref, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Reference: " + ref));
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}
