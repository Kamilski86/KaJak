package com.canda.epcis.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage 4 validation: validates a rendered EPCIS 2.0 JSON document against
 * the official GS1 EPCIS 2.0 JSON Schema (Draft-07).
 *
 * Schema source: https://ref.gs1.org/standards/epcis/2.0.0/epcis-json-schema.json
 * Bundled at: classpath:schema/epcis-2.0-json-schema.json
 */
@Component
public class Epcis2JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(Epcis2JsonSchemaValidator.class);

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public Epcis2JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    /**
     * Validates any DTO object (e.g. EpcisDocumentDto) against the GS1 EPCIS 2.0 JSON Schema.
     * The object is serialised to a JsonNode via the configured ObjectMapper.
     */
    public void validate(Object document) {
        try {
            JsonNode node = objectMapper.valueToTree(document);
            runValidation(node);
        } catch (Epcis2SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new Epcis2SchemaValidationException(
                    "Failed to serialise document for schema validation: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a pre-serialised EPCIS 2.0 JSON string against the GS1 schema.
     */
    public void validate(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            runValidation(node);
        } catch (Epcis2SchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new Epcis2SchemaValidationException(
                    "Failed to parse JSON for schema validation: " + e.getMessage(), e);
        }
    }

    private void runValidation(JsonNode node) {
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            String details = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining("; "));
            log.warn("EPCIS 2.0 schema validation failed: {}", details);
            throw new Epcis2SchemaValidationException(
                    "Rendered output does not conform to GS1 EPCIS 2.0 JSON Schema: " + details);
        }
        log.debug("EPCIS 2.0 JSON schema validation passed");
    }

    private JsonSchema loadSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("schema/epcis-2.0-json-schema.json");
            try (InputStream is = resource.getInputStream()) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                JsonSchema loaded = factory.getSchema(is);
                log.info("GS1 EPCIS 2.0 JSON Schema loaded from classpath:schema/epcis-2.0-json-schema.json");
                return loaded;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load GS1 EPCIS 2.0 JSON Schema", e);
        }
    }
}
