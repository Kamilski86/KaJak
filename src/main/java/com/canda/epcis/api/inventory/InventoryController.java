package com.canda.epcis.api.inventory;

import com.canda.epcis.application.inventory.InventoryQueryService;
import com.canda.epcis.application.inventory.InventoryRebuildService;
import com.canda.epcis.domain.model.EpcState;
import com.canda.epcis.domain.model.SsccContent;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryQueryService queryService;
    private final InventoryRebuildService rebuildService;

    public InventoryController(InventoryQueryService queryService,
                               InventoryRebuildService rebuildService) {
        this.queryService = queryService;
        this.rebuildService = rebuildService;
    }

    // ─────────────────────────────────────────────
    // 1. GET /inventory/epc — EPC State
    // ─────────────────────────────────────────────

    @GetMapping("/epc")
    public ResponseEntity<EpcStateResponse> getEpcState(@RequestParam String epc) {
        EpcState state = queryService.getEpcState(epc);
        return ResponseEntity.ok(toEpcStateResponse(state));
    }

    // ─────────────────────────────────────────────
    // 2. GET /inventory/stock — Bestand an einem Standort
    // ─────────────────────────────────────────────

    @GetMapping("/stock")
    public ResponseEntity<StockResponse> getStock(
            @RequestParam String gln,
            @RequestParam(defaultValue = "false") boolean availableOnly) {

        List<EpcState> items = queryService.getStockAtLocation(gln, availableOnly);
        List<EpcStateResponse> responseItems = items.stream()
                .map(this::toEpcStateResponse)
                .toList();

        return ResponseEntity.ok(StockResponse.builder()
                .gln(gln)
                .totalCount(responseItems.size())
                .items(responseItems)
                .build());
    }

    // ─────────────────────────────────────────────
    // 3. GET /inventory/quantity — Anzahl Einheiten einer GTIN
    // ─────────────────────────────────────────────

    @GetMapping("/quantity")
    public ResponseEntity<Map<String, Object>> getQuantity(
            @RequestParam String gtin,
            @RequestParam(required = false) String gln,
            @RequestParam(defaultValue = "true") boolean availableOnly) {

        long quantity = queryService.getQuantityByGtin(gtin, gln, availableOnly);

        return ResponseEntity.ok(Map.of(
                "gtin", gtin,
                "gln", gln != null ? gln : "",
                "availableOnly", availableOnly,
                "quantity", quantity));
    }

    // ─────────────────────────────────────────────
    // 4. GET /inventory/pallet — SSCC-Inhalt
    // ─────────────────────────────────────────────

    @GetMapping("/pallet")
    public ResponseEntity<PalletResponse> getPallet(@RequestParam String sscc) {
        SsccContent content = queryService.getPalletContent(sscc);
        return ResponseEntity.ok(PalletResponse.builder()
                .ssccUrn(content.getSsccUrn())
                .bizLocation(content.getBizLocation())
                .childCount(content.getChildEpcs().size())
                .childEpcs(content.getChildEpcs())
                .lastUpdated(content.getLastUpdated())
                .build());
    }

    // ─────────────────────────────────────────────
    // 5. GET /inventory/history — Bewegungshistorie
    // ─────────────────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<MovementHistoryResponse> getHistory(
            @RequestParam String epc,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "100") int limit) {

        List<MovementHistoryEntity> entries = queryService.getMovementHistory(epc, from, to, limit);

        List<MovementHistoryResponse.MovementEntry> movements = entries.stream()
                .map(e -> MovementHistoryResponse.MovementEntry.builder()
                        .eventId(e.getEventId())
                        .eventType(e.getEventType())
                        .action(e.getAction())
                        .bizStep(e.getBizStep())
                        .disposition(e.getDisposition())
                        .bizLocation(e.getBizLocation())
                        .readPoint(e.getReadPoint())
                        .eventTime(e.getEventTime())
                        .build())
                .toList();

        return ResponseEntity.ok(MovementHistoryResponse.builder()
                .epcUrn(epc)
                .totalCount(movements.size())
                .movements(movements)
                .build());
    }

    // ─────────────────────────────────────────────
    // 6. GET /inventory/available-quantity — GSPM-Format (REQ301)
    // ─────────────────────────────────────────────

    @GetMapping("/available-quantity")
    public ResponseEntity<AvailableQuantityResponse> getAvailableQuantity(
            @RequestParam(required = false) String gln) {

        Map<String, Map<String, Long>> grouped = queryService.getAvailableQuantityGrouped(gln);

        List<AvailableQuantityResponse.LocationQuantity> locations = new ArrayList<>();
        grouped.forEach((sgln, gtinMap) -> {
            List<AvailableQuantityResponse.GtinQuantity> items = gtinMap.entrySet().stream()
                    .map(e -> AvailableQuantityResponse.GtinQuantity.builder()
                            .gtin(e.getKey())
                            .qty(e.getValue().intValue())
                            .build())
                    .toList();
            locations.add(AvailableQuantityResponse.LocationQuantity.builder()
                    .sgln(sgln)
                    .items(items)
                    .build());
        });

        return ResponseEntity.ok(AvailableQuantityResponse.builder()
                .timestamp(OffsetDateTime.now())
                .messageId(UUID.randomUUID().toString())
                .locations(locations)
                .build());
    }

    // ─────────────────────────────────────────────
    // 7. POST /inventory/rebuild
    // ─────────────────────────────────────────────

    @PostMapping("/rebuild")
    public ResponseEntity<RebuildResponse> rebuild() {
        InventoryRebuildService.RebuildResult result = rebuildService.rebuild();
        return ResponseEntity.ok(RebuildResponse.builder()
                .eventsProcessed(result.getEventsProcessed())
                .eventsSkipped(result.getEventsSkipped())
                .errors(result.getErrors())
                .durationMs(result.getDurationMs())
                .completedAt(result.getCompletedAt())
                .build());
    }

    // ─────────────────────────────────────────────
    // Mapping: Domain → Response DTO
    // ─────────────────────────────────────────────

    private EpcStateResponse toEpcStateResponse(EpcState state) {
        return EpcStateResponse.builder()
                .epcUrn(state.getEpcUrn())
                .currentStatus(state.getCurrentStatus())
                .bizLocation(state.getBizLocation())
                .lastEventId(state.getLastEventId())
                .sscc(state.getSscc())
                .lastEventTime(state.getLastEventTime())
                .createDate(state.getCreateDate())
                .updateDate(state.getUpdateDate())
                .encodedAt(state.getEncodedAt())
                .inProgressAt(state.getInProgressAt())
                .inTransitAt(state.getInTransitAt())
                .accessibleForCustomerAt(state.getAccessibleForCustomerAt())
                .availableNotAccessibleForCustomerAt(state.getAvailableNotAccessibleForCustomerAt())
                .retiredAt(state.getRetiredAt())
                .soldAt(state.getSoldAt())
                .build();
    }
}
