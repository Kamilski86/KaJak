package com.example.epcis.infrastructure.xml;

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

@Component
public class EpcisXmlValidator {

    private static final Logger log = LoggerFactory.getLogger(EpcisXmlValidator.class);

    private final Schema schema;

    public EpcisXmlValidator(@Value("${epcis.schema.path}") Resource schemaResource) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            this.schema = factory.newSchema(schemaResource.getURL());
            log.info("EPCIS 1.2 XSD schema loaded: {}", schemaResource.getFilename());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load XSD schema: " + e.getMessage(), e);
        }
    }

    public void validate(String xml) {
        try {
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new StringReader(xml)));
            log.debug("XML schema validation passed");
        } catch (Exception e) {
            log.warn("XML schema validation failed: {}", e.getMessage());
            throw new EpcisValidationException("Invalid EPCIS 1.2 XML: " + e.getMessage(), e);
        }
    }
}
