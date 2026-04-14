package com.canda.epcis.infrastructure.xml;

import com.canda.epcis.application.port.EventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.InputStream;
import java.io.StringReader;

@Component
public class EpcisXmlValidator implements EventValidator {

    private static final Logger log = LoggerFactory.getLogger(EpcisXmlValidator.class);

    private static final String XSD_BASE = "/xsd/";

    private final Schema schema;

    public EpcisXmlValidator() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setResourceResolver(new ClasspathXsdResolver());

            try (InputStream is = EpcisXmlValidator.class.getResourceAsStream(XSD_BASE + "EPCglobal-epcis-1_2.xsd")) {
                if (is == null) {
                    throw new IllegalStateException("XSD not found on classpath: " + XSD_BASE + "EPCglobal-epcis-1_2.xsd");
                }
                this.schema = factory.newSchema(new StreamSource(is));
            }
            log.info("EPCIS 1.2 XSD schema loaded from classpath");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load XSD schema: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves XSD imports (EPCglobal.xsd, StandardBusinessDocumentHeader.xsd) from the
     * classpath. Required when running as a Spring Boot fat-jar where jar:nested: URLs
     * cannot be resolved by the Xerces schema factory directly.
     */
    private static class ClasspathXsdResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            if (systemId == null) {
                return null;
            }
            // Strip leading path components — imports use "./EPCglobal.xsd" or "EPCglobal.xsd"
            String filename = systemId.contains("/") ? systemId.substring(systemId.lastIndexOf('/') + 1) : systemId;
            InputStream stream = EpcisXmlValidator.class.getResourceAsStream(XSD_BASE + filename);
            if (stream == null) {
                return null;
            }
            return new StreamLSInput(publicId, systemId, stream);
        }
    }

    private static class StreamLSInput implements LSInput {
        private final String publicId;
        private final String systemId;
        private final InputStream stream;

        StreamLSInput(String publicId, String systemId, InputStream stream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.stream = stream;
        }

        @Override public InputStream getByteStream() { return stream; }
        @Override public String getPublicId() { return publicId; }
        @Override public String getSystemId() { return systemId; }
        @Override public String getBaseURI() { return null; }
        @Override public java.io.Reader getCharacterStream() { return null; }
        @Override public String getStringData() { return null; }
        @Override public String getEncoding() { return null; }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setByteStream(InputStream byteStream) {}
        @Override public void setCharacterStream(java.io.Reader characterStream) {}
        @Override public void setStringData(String stringData) {}
        @Override public void setSystemId(String systemId) {}
        @Override public void setPublicId(String publicId) {}
        @Override public void setBaseURI(String baseURI) {}
        @Override public void setEncoding(String encoding) {}
        @Override public void setCertifiedText(boolean certifiedText) {}
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
