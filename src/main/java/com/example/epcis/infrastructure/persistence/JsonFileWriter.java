package com.example.epcis.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Writes converted EPCIS 2.0 JSON events to files as a best-effort audit log.
 * Database persistence is handled by JsonDatabaseWriter.
 */
@Component
public class JsonFileWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonFileWriter.class);

    private final Path outputDirectory;

    public JsonFileWriter(@Value("${epcis.output.directory}") String outputDir) {
        this.outputDirectory = Paths.get(outputDir);
        createDirectoryIfNotExists();
    }

    public void write(String json) {
        String filename = "epcis-event-" + UUID.randomUUID() + ".json";
        Path target = outputDirectory.resolve(filename);
        try {
            Files.writeString(target, json, StandardCharsets.UTF_8);
            log.info("Event written to file: {}", target.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON file: " + target, e);
        }
    }

    private void createDirectoryIfNotExists() {
        try {
            Files.createDirectories(outputDirectory); // idempotent — no-ops if already exists
            log.info("Output directory ready: {}", outputDirectory.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory: " + outputDirectory, e);
        }
    }
}
