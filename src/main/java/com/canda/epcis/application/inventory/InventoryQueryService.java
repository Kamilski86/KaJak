package com.canda.epcis.application.inventory;

import com.canda.epcis.domain.model.EpcState;
import com.canda.epcis.domain.model.SsccContent;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateEntity;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryEntity;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lesezugriff auf den Inventory-State.
 * Alle schreibenden Operationen liegen im InventoryProcessorService.
 */
@Service
public class InventoryQueryService {

    private static final String SGTIN_PREFIX = "urn:epc:id:sgtin:";

    private final EpcStateRepository epcStateRepository;
    private final SsccContentRepository ssccContentRepository;
    private final MovementHistoryRepository movementHistoryRepository;
    private final com.canda.epcis.config.InventoryConfig inventoryConfig;

    public InventoryQueryService(EpcStateRepository epcStateRepository,
                                 SsccContentRepository ssccContentRepository,
                                 MovementHistoryRepository movementHistoryRepository,
                                 com.canda.epcis.config.InventoryConfig inventoryConfig) {
        this.epcStateRepository = epcStateRepository;
        this.ssccContentRepository = ssccContentRepository;
        this.movementHistoryRepository = movementHistoryRepository;
        this.inventoryConfig = inventoryConfig;
    }

    // ─────────────────────────────────────────────
    // EPC State
    // ─────────────────────────────────────────────

    /**
     * Aktuellen Zustand einer SGTIN abfragen.
     * @throws EpcNotFoundException wenn EPC nicht bekannt (→ HTTP 404)
     */
    public EpcState getEpcState(String epcUrn) {
        return epcStateRepository.findByEpcUrn(epcUrn)
                .map(this::toEpcState)
                .orElseThrow(() -> new EpcNotFoundException(epcUrn));
    }

    // ─────────────────────────────────────────────
    // Stock at Location
    // ─────────────────────────────────────────────

    /**
     * Alle SGTINs an einem bestimmten Standort.
     * @param gln          SGLN URI des Standorts
     * @param availableOnly wenn true: nur SGTINs in Available-Merchandise-Dispositionen (REQ301)
     */
    public List<EpcState> getStockAtLocation(String gln, boolean availableOnly) {
        List<EpcStateEntity> entities;
        if (availableOnly) {
            entities = epcStateRepository.findByBizLocationPrefixAndCurrentStatusIn(
                    gln, inventoryConfig.getAvailableMerchandiseDispositions());
        } else {
            entities = epcStateRepository.findByBizLocation(gln);
        }
        return entities.stream().map(this::toEpcState).toList();
    }

    // ─────────────────────────────────────────────
    // Quantity by GTIN
    // ─────────────────────────────────────────────

    /**
     * Anzahl Einheiten einer GTIN (alle Serialnummern).
     * @param gtin         GTIN-Prefix (ohne Seriennummer), z.B. "urn:epc:id:sgtin:4056019.010532"
     * @param gln          Optional — nur an diesem Standort zählen
     * @param availableOnly wenn true: nur Available-Merchandise Dispositionen (REQ301), default true
     */
    public long getQuantityByGtin(String gtin, String gln, boolean availableOnly) {
        // GTIN-Prefix mit Punkt-Separator sicherstellen
        String gtinPrefix = gtin.endsWith(".") ? gtin : gtin + ".";
        List<EpcStateEntity> entities = epcStateRepository.findByGtinPrefix(gtinPrefix);

        return entities.stream()
                .filter(e -> gln == null || gln.isBlank() || gln.equals(e.getBizLocation()))
                .filter(e -> !availableOnly
                        || inventoryConfig.getAvailableMerchandiseDispositions()
                                          .contains(e.getCurrentStatus()))
                .count();
    }

    // ─────────────────────────────────────────────
    // Pallet Content
    // ─────────────────────────────────────────────

    /**
     * Inhalt einer SSCC (Palette).
     * @throws EpcNotFoundException wenn SSCC nicht bekannt (→ HTTP 404)
     */
    public SsccContent getPalletContent(String ssccUrn) {
        List<com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity> rows =
                ssccContentRepository.findBySsccUrn(ssccUrn);
        if (rows.isEmpty()) {
            throw new EpcNotFoundException(ssccUrn);
        }

        String bizLocation = rows.get(0).getBizLocation();
        OffsetDateTime lastUpdated = rows.stream()
                .map(com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity::getAddedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return SsccContent.builder()
                .ssccUrn(ssccUrn)
                .childEpcs(rows.stream()
                        .map(com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity::getChildEpc)
                        .toList())
                .bizLocation(bizLocation)
                .lastUpdated(lastUpdated)
                .build();
    }

    // ─────────────────────────────────────────────
    // Movement History
    // ─────────────────────────────────────────────

    /**
     * Vollständige Bewegungshistorie einer EPC.
     * @param epcUrn  SGTIN oder SSCC URI
     * @param from    Optional — Zeitraum von
     * @param to      Optional — Zeitraum bis
     * @param limit   Max Anzahl Einträge (default 100, max 1000)
     */
    public List<MovementHistoryEntity> getMovementHistory(String epcUrn,
                                                          OffsetDateTime from,
                                                          OffsetDateTime to,
                                                          int limit) {
        int effectiveLimit = Math.min(limit, inventoryConfig.getMaxHistoryLimit());

        List<MovementHistoryEntity> entries;
        if (from != null && to != null) {
            entries = movementHistoryRepository
                    .findByEpcUrnAndEventTimeBetweenOrderByEventTimeDesc(epcUrn, from, to);
        } else {
            entries = movementHistoryRepository.findByEpcUrnOrderByEventTimeDesc(epcUrn);
        }

        return entries.size() > effectiveLimit ? entries.subList(0, effectiveLimit) : entries;
    }

    // ─────────────────────────────────────────────
    // Available Quantity (REQ301 — GSPM-Format)
    // ─────────────────────────────────────────────

    /**
     * Aggregiert verfügbare Mengen pro GTIN-13 und SGLN.
     * REQ301: nur SGTINs in Available-Merchandise-Dispositionen werden gezählt.
     * @param gln Optional — nur für diesen Store
     * @return gruppierte Liste: pro Standort → pro GTIN-13 → Menge
     */
    public java.util.Map<String, java.util.Map<String, Long>> getAvailableQuantityGrouped(String gln) {
        List<EpcStateEntity> available = epcStateRepository.findAvailableMerchandise(
                inventoryConfig.getAvailableMerchandiseDispositions(),
                (gln != null && !gln.isBlank()) ? gln : null);

        // Gruppierung: bizLocation → gtin13 → count
        return available.stream()
                .filter(e -> e.getBizLocation() != null)
                .filter(e -> sgtinToGtin13(e.getEpcUrn()).isPresent())
                .collect(Collectors.groupingBy(
                        EpcStateEntity::getBizLocation,
                        Collectors.groupingBy(
                                e -> sgtinToGtin13(e.getEpcUrn()).get(),
                                Collectors.counting())));
    }

    // ─────────────────────────────────────────────
    // SGTIN → GTIN-13 Konvertierung
    // ─────────────────────────────────────────────

    /**
     * Konvertiert eine SGTIN URN zu einer GTIN-13.
     *
     * Eingabe:  urn:epc:id:sgtin:4056019.010532.4293918790
     * Ausgabe:  4056019010532
     *
     * Algorithmus:
     * 1. Prefix "urn:epc:id:sgtin:" entfernen
     * 2. An "." splitten → [companyPrefix, itemReference, serial]
     * 3. companyPrefix + itemReference zusammenführen
     * 4. Auf 13 Stellen auffüllen (linksbündig, führende Nullen rechts)
     *
     * Ungültige SGTINs werden mit empty() zurückgegeben.
     */
    public static Optional<String> sgtinToGtin13(String sgtinUrn) {
        if (sgtinUrn == null || !sgtinUrn.startsWith(SGTIN_PREFIX)) {
            return Optional.empty();
        }
        try {
            String withoutPrefix = sgtinUrn.substring(SGTIN_PREFIX.length());
            String[] parts = withoutPrefix.split("\\.");
            if (parts.length < 3) return Optional.empty();

            String companyPrefix = parts[0];
            String itemReference = parts[1];
            String combined = companyPrefix + itemReference;

            // GTIN-13: genau 13 Stellen — ggf. mit führenden Nullen auffüllen
            if (combined.length() > 13) return Optional.empty();
            String gtin13 = String.format("%013d", Long.parseLong(combined));
            return Optional.of(gtin13);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    // ─────────────────────────────────────────────
    // Mapping: Entity → Domain
    // ─────────────────────────────────────────────

    private EpcState toEpcState(EpcStateEntity e) {
        return EpcState.builder()
                .epcUrn(e.getEpcUrn())
                .currentStatus(e.getCurrentStatus())
                .bizLocation(e.getBizLocation())
                .lastEventId(e.getLastEventId())
                .lastEventTime(e.getLastEventTime())
                .sscc(e.getSscc())
                .createDate(e.getCreateDate())
                .updateDate(e.getUpdateDate())
                .encodedAt(e.getEncodedAt())
                .inProgressAt(e.getInProgressAt())
                .inTransitAt(e.getInTransitAt())
                .accessibleForCustomerAt(e.getAccessibleForCustomerAt())
                .availableNotAccessibleForCustomerAt(e.getAvailableNotAccessibleAt())
                .retiredAt(e.getRetiredAt())
                .soldAt(e.getSoldAt())
                .build();
    }
}
