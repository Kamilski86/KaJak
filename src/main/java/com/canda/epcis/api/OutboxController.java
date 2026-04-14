package com.canda.epcis.api;

import com.canda.epcis.infrastructure.persistence.outbox.OutboxEntity;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operations API — Monitoring und manuelle Steuerung des Outbox.
 *
 * GET  /ops/outbox/pending      → alle PENDING Messages
 * GET  /ops/outbox/failed       → alle FAILED Messages
 * POST /ops/outbox/{id}/retry   → einzelne Message manuell auf PENDING zurücksetzen
 * GET  /ops/outbox/stats        → Zähler pro targetSystem + status
 */
@RestController
@RequestMapping("/ops/outbox")
public class OutboxController {

    private final OutboxRepository outboxRepository;

    public OutboxController(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @GetMapping("/pending")
    public List<OutboxEntity> getPending() {
        return outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    }

    @GetMapping("/failed")
    public List<OutboxEntity> getFailed() {
        return outboxRepository.findByStatusOrderByCreatedAtAsc("FAILED");
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public ResponseEntity<OutboxEntity> retry(@PathVariable Long id) {
        return outboxRepository.findById(id)
                .map(msg -> {
                    msg.setStatus("PENDING");
                    msg.setRetryCount(0);
                    msg.setLastError(null);
                    return ResponseEntity.ok(outboxRepository.save(msg));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats")
    public List<Map<String, Object>> getStats() {
        List<Object[]> rows = outboxRepository.countGroupedByTargetAndStatus();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("targetSystem", row[0]);
            entry.put("status",       row[1]);
            entry.put("count",        row[2]);
            result.add(entry);
        }
        return result;
    }
}
