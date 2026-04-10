package infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Schreibt konvertierte EPCIS 2.0 JSON Events in Dateien.
 *
 * Jedes Event bekommt eine eigene Datei mit Timestamp im Namen.
 * Ausgabeverzeichnis wird aus application.yml gelesen: epcis.output.directory
 *
 * Später: wird durch ein PostgreSQL-Repository ersetzt.
 */
@Component
public class JsonFileWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonFileWriter.class);

    private final Path outputDirectory;

    public JsonFileWriter(@Value("${epcis.output.directory}") String outputDir) {
        this.outputDirectory = Paths.get(outputDir);
        createDirectoryIfNotExists();
    }

    /**
     * Schreibt einen JSON-String als Datei.
     * Dateiname: epcis-event-{timestamp}.json
     *
     * @param json EPCIS 2.0 JSON als String
     */
    public void write(String json) {
        String filename = "epcis-event-" + Instant.now().toEpochMilli() + ".json";
        Path target = outputDirectory.resolve(filename);

        try {
            Files.writeString(target, json, StandardCharsets.UTF_8);
            log.info("Event gespeichert: {}", target.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("JSON-Datei konnte nicht geschrieben werden: " + target, e);
        }
    }

    private void createDirectoryIfNotExists() {
        try {
            if (!Files.exists(outputDirectory)) {
                Files.createDirectories(outputDirectory);
                log.info("Output-Verzeichnis erstellt: {}", outputDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Output-Verzeichnis konnte nicht erstellt werden: " + outputDirectory, e);
        }
    }
}
