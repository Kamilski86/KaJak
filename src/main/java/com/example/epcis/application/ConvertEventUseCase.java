package application;

import domain.model.EpcisEvent;
import infrastructure.json.Epcis2JsonRenderer;
import infrastructure.persistence.JsonFileWriter;
import infrastructure.xml.EpcisXmlParser;
import infrastructure.xml.EpcisXmlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use Case: Konvertiert EPCIS 1.2 XML → EPCIS 2.0 JSON.
 *
 * Ablauf:
 * 1. XML validieren (XSD)
 * 2. XML parsen → Domain-Objekte
 * 3. Domain-Objekte → EPCIS 2.0 JSON rendern
 * 4. JSON in Datei schreiben
 *
 * Diese Klasse orchestriert — sie enthält keine eigene Geschäftslogik.
 */
@Service
public class ConvertEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConvertEventUseCase.class);

    private final EpcisXmlValidator validator;
    private final EpcisXmlParser parser;
    private final Epcis2JsonRenderer renderer;
    private final JsonFileWriter fileWriter;

    public ConvertEventUseCase(EpcisXmlValidator validator,
                               EpcisXmlParser parser,
                               Epcis2JsonRenderer renderer,
                               JsonFileWriter fileWriter) {
        this.validator = validator;
        this.parser = parser;
        this.renderer = renderer;
        this.fileWriter = fileWriter;
    }

    /**
     * Führt die vollständige Konvertierung durch.
     *
     * @param xml EPCIS 1.2 XML als String
     * @return Liste der erzeugten JSON-Strings (ein Eintrag pro Event)
     */
    public List<String> convert(String xml) {
        log.info("Konvertierung gestartet");

        // Schritt 1: XSD-Validierung — wirft EpcisValidationException bei Fehler → HTTP 400
        validator.validate(xml);

        // Schritt 2: Parsen → Domain-Objekte
        List<EpcisEvent> events = parser.parse(xml);
        log.info("{} Event(s) geparst", events.size());

        // Schritt 3 + 4: Rendern und speichern
        List<String> results = events.stream()
                .map(event -> {
                    String json = renderer.render(event);
                    fileWriter.write(json);
                    return json;
                })
                .toList();

        log.info("Konvertierung abgeschlossen: {} Event(s) geschrieben", results.size());
        return results;
    }
}
