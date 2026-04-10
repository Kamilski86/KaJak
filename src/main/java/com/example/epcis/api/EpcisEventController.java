package api;

import application.ConvertEventUseCase;
import infrastructure.xml.EpcisValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST Controller – Eingang für EPCIS 1.2 XML Events.
 *
 * POST /api/events/convert
 *   Content-Type: application/xml
 *   Body: EPCIS 1.2 XML
 *
 * Response:
 *   200 → Liste der konvertierten EPCIS 2.0 JSON Events
 *   400 → Validierungsfehler (ungültiges XML oder Schema-Verletzung)
 *   500 → Interner Fehler
 */
@RestController
@RequestMapping("/api/events")
public class EpcisEventController {

    private static final Logger log = LoggerFactory.getLogger(EpcisEventController.class);

    private final ConvertEventUseCase convertEventUseCase;

    public EpcisEventController(ConvertEventUseCase convertEventUseCase) {
        this.convertEventUseCase = convertEventUseCase;
    }

    /**
     * Nimmt EPCIS 1.2 XML entgegen, konvertiert zu EPCIS 2.0 JSON.
     *
     * @param xml EPCIS 1.2 XML als String im Request Body
     * @return Liste der konvertierten JSON-Events
     */
    @PostMapping(
        value = "/convert",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<List<String>> convert(@RequestBody String xml) {
        log.info("POST /api/events/convert empfangen ({} Zeichen)", xml.length());
        List<String> results = convertEventUseCase.convert(xml);
        return ResponseEntity.ok(results);
    }

    /**
     * Fängt Validierungsfehler und gibt HTTP 400 zurück.
     * Kein Stack-Trace an den Client — nur die Fehlermeldung.
     */
    @ExceptionHandler(EpcisValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(EpcisValidationException ex) {
        log.warn("Validierungsfehler: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Fängt alle anderen Fehler und gibt HTTP 500 zurück.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unerwarteter Fehler: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Interner Fehler: " + ex.getMessage()));
    }
}
