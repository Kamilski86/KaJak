package com.canda.epcis.api;

import com.canda.epcis.infrastructure.persistence.QuarantineEntity;
import com.canda.epcis.infrastructure.persistence.QuarantineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quarantine")
public class QuarantineController {

    private static final Logger log = LoggerFactory.getLogger(QuarantineController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final QuarantineRepository repository;

    public QuarantineController(QuarantineRepository repository) {
        this.repository = repository;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<QuarantineEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<QuarantineEntity> result = repository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
        log.info("GET /api/quarantine → {} items (page {}/{})",
                result.getNumberOfElements(), page, result.getTotalPages());
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QuarantineEntity> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("GET /api/quarantine/{} → not found", id);
                    return ResponseEntity.notFound().build();
                });
    }
}
