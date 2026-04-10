package infrastructure.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;

/**
 * Validiert eingehendes EPCIS 1.2 XML gegen das offizielle GS1 XSD-Schema.
 * Schlägt die Validierung fehl, wird eine EpcisValidationException geworfen.
 * Der REST-Controller wandelt diese in HTTP 400 um.
 */
@Component
public class EpcisXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(EpcisXmlValidator.class);

    private final Schema schema;

    /**
     * Lädt die XSD-Datei beim Start der Anwendung einmalig.
     * Pfad wird aus application.yml gelesen: epcis.schema.path
     */
    public EpcisXmlValidator(@Value("${epcis.schema.path}") Resource schemaResource) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // XXE-Schutz: externe Entities und DTDs deaktivieren
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

            this.schema = factory.newSchema(schemaResource.getURL());
            log.info("EPCIS 1.2 XSD-Schema geladen: {}", schemaResource.getFilename());
        } catch (Exception e) {
            throw new IllegalStateException("XSD-Schema konnte nicht geladen werden: " + e.getMessage(), e);
        }
    }

    /**
     * Validiert den XML-String gegen das EPCIS 1.2 Schema.
     *
     * @param xml Eingehendes EPCIS 1.2 XML als String
     * @throws EpcisValidationException wenn das XML nicht schema-konform ist
     */
    public void validate(String xml) {
        try {
            Validator validator = schema.newValidator();

            // XXE-Schutz auf dem Validator
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            validator.validate(new StreamSource(new StringReader(xml)));
            log.debug("XML-Validierung erfolgreich");
        } catch (Exception e) {
            log.warn("XML-Validierung fehlgeschlagen: {}", e.getMessage());
            throw new EpcisValidationException("Ungültiges EPCIS 1.2 XML: " + e.getMessage(), e);
        }
    }
}
