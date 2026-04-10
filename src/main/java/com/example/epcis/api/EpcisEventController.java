package com.example.epcis.api;

import com.example.epcis.application.ConvertEventUseCase;
import com.example.epcis.infrastructure.json.EpcisDocumentDto;
import com.example.epcis.infrastructure.persistence.EpcisEventEntity;
import com.example.epcis.infrastructure.persistence.EpcisEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/events")
public class EpcisEventController {

    private static final Logger log = LoggerFactory.getLogger(EpcisEventController.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final ConvertEventUseCase convertEventUseCase;
    private final EpcisEventRepository repository;
    private final ObjectMapper objectMapper;

    public EpcisEventController(ConvertEventUseCase convertEventUseCase,
                                EpcisEventRepository repository,
                                ObjectMapper objectMapper) {
        this.convertEventUseCase = convertEventUseCase;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/convert", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EpcisDocumentDto> convert(@RequestBody String xml) {
        log.info("POST /api/events/convert received ({} chars)", xml.length());
        return ResponseEntity.ok(convertEventUseCase.convert(xml));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<EpcisEventResponse>> search(
            @ModelAttribute EventSearchCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<EpcisEventResponse> response = repository
                .search(criteria.getType(), criteria.getAction(), criteria.getBizStep(),
                        criteria.getDisposition(), criteria.getReadPoint(), criteria.getBizLocation(),
                        criteria.getParentId(), criteria.getEpc(), criteria.getGln(),
                        criteria.getEventTimeFrom(), criteria.getEventTimeTo(),
                        PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)))
                .map(this::toResponse);

        log.info("GET /api/events → {} results (page {}/{})",
                response.getNumberOfElements(), page, response.getTotalPages());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EpcisEventResponse> getById(@PathVariable Long id) {
        Optional<EpcisEventEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            log.warn("GET /api/events/{} → not found", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(entity.get()));
    }

    private EpcisEventResponse toResponse(EpcisEventEntity entity) {
        Object parsedPayload;
        try {
            parsedPayload = objectMapper.readValue(entity.getPayload(), Object.class);
        } catch (Exception e) {
            log.warn("Could not parse payload for id={}", entity.getId());
            parsedPayload = entity.getPayload();
        }
        return EpcisEventResponse.builder()
                .id(entity.getId())
                .eventType(entity.getEventType())
                .eventTime(entity.getEventTime())
                .createdAt(entity.getCreatedAt())
                .payload(parsedPayload)
                .build();
    }
}
