package com.canda.epcis.application.inventory;

import com.canda.epcis.config.InventoryConfig;
import com.canda.epcis.domain.model.EpcState;
import com.canda.epcis.domain.model.SsccContent;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateEntity;
import com.canda.epcis.infrastructure.persistence.inventory.EpcStateRepository;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryEntity;
import com.canda.epcis.infrastructure.persistence.inventory.MovementHistoryRepository;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentEntity;
import com.canda.epcis.infrastructure.persistence.inventory.SsccContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTest {

    @Mock EpcStateRepository        epcStateRepository;
    @Mock SsccContentRepository     ssccContentRepository;
    @Mock MovementHistoryRepository movementHistoryRepository;

    private InventoryQueryService queryService;

    private static final String SGTIN_A = "urn:epc:id:sgtin:4056019.010532.0000001";
    private static final String SGTIN_B = "urn:epc:id:sgtin:4056019.010532.0000002";
    private static final String SGTIN_C = "urn:epc:id:sgtin:4056019.010532.0000003";
    private static final String SSCC    = "urn:epc:id:sscc:4056019.0000000001";
    private static final String GLN     = "urn:epc:id:sgln:4056019.00000.0";

    @BeforeEach
    void setUp() {
        InventoryConfig config = new InventoryConfig();
        config.setMaxHistoryLimit(1000);
        config.setDefaultHistoryLimit(100);
        config.setAvailableMerchandiseDispositions(
                List.of("sellable_accessible", "sellable_not_accessible"));
        queryService = new InventoryQueryService(
                epcStateRepository, ssccContentRepository, movementHistoryRepository, config);
    }

    // ─────────────────────────────────────────────
    // getEpcState
    // ─────────────────────────────────────────────

    @Test
    void getEpcState_knownEpc_returnsEpcState() {
        when(epcStateRepository.findByEpcUrn(SGTIN_A))
                .thenReturn(Optional.of(buildEntity(SGTIN_A, "sellable_accessible", GLN)));

        EpcState state = queryService.getEpcState(SGTIN_A);

        assertThat(state.getEpcUrn()).isEqualTo(SGTIN_A);
        assertThat(state.getCurrentStatus()).isEqualTo("sellable_accessible");
        assertThat(state.getBizLocation()).isEqualTo(GLN);
    }

    @Test
    void getEpcState_unknownEpc_throwsEpcNotFoundException() {
        when(epcStateRepository.findByEpcUrn(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getEpcState("urn:epc:id:sgtin:0000.000000.9999"))
                .isInstanceOf(EpcNotFoundException.class);
    }

    // ─────────────────────────────────────────────
    // getStockAtLocation
    // ─────────────────────────────────────────────

    @Test
    void getStockAtLocation_glnWithItems_returnsCorrectList() {
        when(epcStateRepository.findByBizLocation(GLN))
                .thenReturn(List.of(
                        buildEntity(SGTIN_A, "sellable_accessible", GLN),
                        buildEntity(SGTIN_B, "in_transit", GLN)));

        List<EpcState> result = queryService.getStockAtLocation(GLN, false);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(EpcState::getEpcUrn).containsExactlyInAnyOrder(SGTIN_A, SGTIN_B);
    }

    @Test
    void getStockAtLocation_glnWithNoItems_returnsEmptyList() {
        when(epcStateRepository.findByBizLocation(GLN)).thenReturn(List.of());

        List<EpcState> result = queryService.getStockAtLocation(GLN, false);

        assertThat(result).isEmpty();
    }

    @Test
    void getStockAtLocation_availableOnly_usesDispositionFilter() {
        when(epcStateRepository.findByBizLocationPrefixAndCurrentStatusIn(
                anyString(), anyList()))
                .thenReturn(List.of(buildEntity(SGTIN_A, "sellable_accessible", GLN)));

        List<EpcState> result = queryService.getStockAtLocation(GLN, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrentStatus()).isEqualTo("sellable_accessible");
    }

    // ─────────────────────────────────────────────
    // getQuantityByGtin
    // ─────────────────────────────────────────────

    @Test
    void getQuantityByGtin_gtinWithThreeSerials_returnsThree() {
        String gtinPrefix = "urn:epc:id:sgtin:4056019.010532.";
        when(epcStateRepository.findByGtinPrefix(gtinPrefix))
                .thenReturn(List.of(
                        buildEntity(SGTIN_A, "sellable_accessible", GLN),
                        buildEntity(SGTIN_B, "sellable_accessible", GLN),
                        buildEntity(SGTIN_C, "sellable_not_accessible", GLN)));

        long qty = queryService.getQuantityByGtin("urn:epc:id:sgtin:4056019.010532", null, true);

        assertThat(qty).isEqualTo(3);
    }

    @Test
    void getQuantityByGtin_withGlnFilter_countsOnlyMatchingLocation() {
        String otherGln = "urn:epc:id:sgln:4056019.00001.0";
        String gtinPrefix = "urn:epc:id:sgtin:4056019.010532.";
        when(epcStateRepository.findByGtinPrefix(gtinPrefix))
                .thenReturn(List.of(
                        buildEntity(SGTIN_A, "sellable_accessible", GLN),
                        buildEntity(SGTIN_B, "sellable_accessible", otherGln)));

        long qty = queryService.getQuantityByGtin("urn:epc:id:sgtin:4056019.010532", GLN, true);

        assertThat(qty).isEqualTo(1);
    }

    // ─────────────────────────────────────────────
    // getPalletContent
    // ─────────────────────────────────────────────

    @Test
    void getPalletContent_ssccWithFiveItems_returnsChildCount5() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SsccContentEntity> rows = List.of(
                buildSsccRow(SSCC, SGTIN_A, GLN, now),
                buildSsccRow(SSCC, SGTIN_B, GLN, now),
                buildSsccRow(SSCC, SGTIN_C, GLN, now),
                buildSsccRow(SSCC, "urn:epc:id:sgtin:4056019.010532.0000004", GLN, now),
                buildSsccRow(SSCC, "urn:epc:id:sgtin:4056019.010532.0000005", GLN, now));
        when(ssccContentRepository.findBySsccUrn(SSCC)).thenReturn(rows);

        SsccContent content = queryService.getPalletContent(SSCC);

        assertThat(content.getSsccUrn()).isEqualTo(SSCC);
        assertThat(content.getChildEpcs()).hasSize(5);
        assertThat(content.getBizLocation()).isEqualTo(GLN);
    }

    @Test
    void getPalletContent_unknownSscc_throwsEpcNotFoundException() {
        when(ssccContentRepository.findBySsccUrn(anyString())).thenReturn(List.of());

        assertThatThrownBy(() -> queryService.getPalletContent("urn:epc:id:sscc:0000000.0000000000"))
                .isInstanceOf(EpcNotFoundException.class);
    }

    // ─────────────────────────────────────────────
    // getMovementHistory
    // ─────────────────────────────────────────────

    @Test
    void getMovementHistory_epcWithThreeMovements_returnsThree() {
        when(movementHistoryRepository.findByEpcUrnOrderByEventTimeDesc(SGTIN_A))
                .thenReturn(List.of(
                        buildMovement(SGTIN_A, "evt-1"),
                        buildMovement(SGTIN_A, "evt-2"),
                        buildMovement(SGTIN_A, "evt-3")));

        List<MovementHistoryEntity> result = queryService.getMovementHistory(SGTIN_A, null, null, 100);

        assertThat(result).hasSize(3);
    }

    @Test
    void getMovementHistory_withTimeRange_usesTimeBetweenQuery() {
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to   = OffsetDateTime.now();
        when(movementHistoryRepository.findByEpcUrnAndEventTimeBetweenOrderByEventTimeDesc(SGTIN_A, from, to))
                .thenReturn(List.of(buildMovement(SGTIN_A, "evt-range-1")));

        List<MovementHistoryEntity> result = queryService.getMovementHistory(SGTIN_A, from, to, 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo("evt-range-1");
    }

    @Test
    void getMovementHistory_limitExceedsMaxHistoryLimit_clampsToMax() {
        List<MovementHistoryEntity> longList = java.util.stream.IntStream.range(0, 1001)
                .mapToObj(i -> buildMovement(SGTIN_A, "evt-" + i))
                .toList();
        when(movementHistoryRepository.findByEpcUrnOrderByEventTimeDesc(SGTIN_A)).thenReturn(longList);

        List<MovementHistoryEntity> result = queryService.getMovementHistory(SGTIN_A, null, null, 5000);

        assertThat(result).hasSize(1000); // clamped to maxHistoryLimit
    }

    // ─────────────────────────────────────────────
    // sgtinToGtin13
    // ─────────────────────────────────────────────

    @Test
    void sgtinToGtin13_validSgtin_returnsCorrectGtin13() {
        // urn:epc:id:sgtin:4056019.010532.4293918790
        // companyPrefix=4056019, itemReference=010532 → combined=4056019010532 → 13 digits
        Optional<String> gtin = InventoryQueryService.sgtinToGtin13(
                "urn:epc:id:sgtin:4056019.010532.4293918790");

        assertThat(gtin).isPresent().hasValue("4056019010532");
    }

    @Test
    void sgtinToGtin13_invalidSgtin_returnsEmpty() {
        assertThat(InventoryQueryService.sgtinToGtin13("not-a-sgtin")).isEmpty();
        assertThat(InventoryQueryService.sgtinToGtin13(null)).isEmpty();
        assertThat(InventoryQueryService.sgtinToGtin13("urn:epc:id:sgtin:only-two-parts")).isEmpty();
    }

    // ─────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────

    private EpcStateEntity buildEntity(String epcUrn, String status, String bizLocation) {
        OffsetDateTime now = OffsetDateTime.now();
        return EpcStateEntity.builder()
                .epcUrn(epcUrn)
                .currentStatus(status)
                .bizLocation(bizLocation)
                .createDate(now)
                .updateDate(now)
                .build();
    }

    private SsccContentEntity buildSsccRow(String sscc, String childEpc, String bizLocation,
                                            OffsetDateTime addedAt) {
        return SsccContentEntity.builder()
                .ssccUrn(sscc)
                .childEpc(childEpc)
                .bizLocation(bizLocation)
                .addedAt(addedAt)
                .build();
    }

    private MovementHistoryEntity buildMovement(String epcUrn, String eventId) {
        return MovementHistoryEntity.builder()
                .epcUrn(epcUrn)
                .eventId(eventId)
                .eventType("ObjectEvent")
                .action("OBSERVE")
                .eventTime(OffsetDateTime.now())
                .recordedAt(OffsetDateTime.now())
                .build();
    }
}
