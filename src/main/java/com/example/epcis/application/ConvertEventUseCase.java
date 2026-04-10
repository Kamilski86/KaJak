package com.example.epcis.application;

import com.example.epcis.domain.model.EpcisEvent;
import com.example.epcis.infrastructure.json.Epcis2JsonRenderer;
import com.example.epcis.infrastructure.json.EpcisDocumentDto;
import com.example.epcis.infrastructure.persistence.JsonDatabaseWriter;
import com.example.epcis.infrastructure.persistence.JsonFileWriter;
import com.example.epcis.infrastructure.xml.EpcisXmlParser;
import com.example.epcis.infrastructure.xml.EpcisXmlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConvertEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConvertEventUseCase.class);

    private final EpcisXmlValidator validator;
    private final EpcisXmlParser parser;
    private final Epcis2JsonRenderer renderer;
    private final JsonFileWriter fileWriter;
    private final JsonDatabaseWriter databaseWriter;

    public ConvertEventUseCase(EpcisXmlValidator validator,
                               EpcisXmlParser parser,
                               Epcis2JsonRenderer renderer,
                               JsonFileWriter fileWriter,
                               JsonDatabaseWriter databaseWriter) {
        this.validator = validator;
        this.parser = parser;
        this.renderer = renderer;
        this.fileWriter = fileWriter;
        this.databaseWriter = databaseWriter;
    }

    @Transactional
    public EpcisDocumentDto convert(String xml) {
        log.info("Conversion started");
        validator.validate(xml);
        List<EpcisEvent> events = parser.parse(xml);
        log.info("{} event(s) parsed", events.size());

        // Render each event individually (no @context) for DB/file storage
        List<String> eventJsons = events.stream()
                .map(event -> {
                    String json = renderer.render(event);
                    databaseWriter.write(event, json);
                    return json;
                })
                .toList();

        // File writes after all DB writes succeed (DB is transactional; files are best-effort audit)
        eventJsons.forEach(fileWriter::write);

        EpcisDocumentDto document = renderer.renderDocument(events);
        log.info("Conversion complete: {} event(s) written", events.size());
        return document;
    }
}
