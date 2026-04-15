package com.canda.epcis.application.digitallink;

import com.canda.epcis.application.inventory.EpcNotFoundException;
import com.canda.epcis.application.inventory.InventoryQueryService;
import com.canda.epcis.config.DigitalLinkConfig;
import com.canda.epcis.domain.model.EpcState;
import com.canda.epcis.domain.model.digitallink.DigitalLinkParseException;
import com.canda.epcis.domain.model.digitallink.ResolverResponse;
import com.canda.epcis.infrastructure.persistence.EpcisEventEntity;
import com.canda.epcis.infrastructure.persistence.EpcisEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.canda.epcis.application.digitallink.DigitalLinkParserTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DigitalLinkResolverServiceTest {

    @Mock EpcisEventRepository  eventRepository;
    @Mock InventoryQueryService  inventoryQueryService;

    DigitalLinkResolverService resolverService;
    DigitalLinkParser parser;

    static final String SGTIN_DL  = BASE_URL + "/01/" + GTIN14 + "/21/" + SERIAL;
    static final String SSCC_DL   = BASE_URL + "/00/" + SSCC18;

    @BeforeEach
    void setUp() {
        parser = new DigitalLinkParser();

        DigitalLinkConfig config = new DigitalLinkConfig();
        config.setBaseUrl(BASE_URL);
        config.setEnabled(true);

        resolverService = new DigitalLinkResolverService(
                parser, eventRepository, inventoryQueryService, config);
    }

    // ─────────────────────────────────────────────
    // resolve — SGTIN
    // ─────────────────────────────────────────────

    @Test
    void resolve_sgtin_includesEpcisLink() {
        when(inventoryQueryService.getEpcState(eq(SGTIN_URN)))
                .thenThrow(new EpcNotFoundException(SGTIN_URN));

        ResolverResponse response = resolverService.resolve(SGTIN_DL);

        assertThat(response.getIdentifier()).isEqualTo(SGTIN_URN);
        assertThat(response.getIdentifierType()).isEqualTo("SGTIN");

        boolean hasEpcisLink = response.getLinkSets().stream()
                .flatMap(ls -> ls.getLinks().stream())
                .anyMatch(l -> "gs1:epcis".equals(l.getLinkType())
                        && l.getHref().contains(SGTIN_URN));
        assertThat(hasEpcisLink).isTrue();
    }

    @Test
    void resolve_sgtin_withInventoryState_includesLocationLink() {
        EpcState state = EpcState.builder()
                .epcUrn(SGTIN_URN)
                .currentStatus("sellable_accessible")
                .bizLocation("urn:epc:id:sgln:" + COMPANY + "." + LOCATION_REF + ".0")
                .lastEventTime(OffsetDateTime.now())
                .build();
        when(inventoryQueryService.getEpcState(eq(SGTIN_URN))).thenReturn(state);

        ResolverResponse response = resolverService.resolve(SGTIN_DL);

        boolean hasLocationLink = response.getLinkSets().stream()
                .flatMap(ls -> ls.getLinks().stream())
                .anyMatch(l -> "gs1:location".equals(l.getLinkType()));
        assertThat(hasLocationLink).isTrue();
    }

    @Test
    void resolve_sgtin_withoutInventoryState_noLocationLink() {
        when(inventoryQueryService.getEpcState(eq(SGTIN_URN)))
                .thenThrow(new EpcNotFoundException(SGTIN_URN));

        ResolverResponse response = resolverService.resolve(SGTIN_DL);

        boolean hasLocationLink = response.getLinkSets().stream()
                .flatMap(ls -> ls.getLinks().stream())
                .anyMatch(l -> "gs1:location".equals(l.getLinkType()));
        assertThat(hasLocationLink).isFalse();
    }

    // ─────────────────────────────────────────────
    // resolve — SSCC
    // ─────────────────────────────────────────────

    @Test
    void resolve_sscc_includesPalletLink() {
        when(inventoryQueryService.getEpcState(eq(SSCC_URN)))
                .thenThrow(new EpcNotFoundException(SSCC_URN));

        ResolverResponse response = resolverService.resolve(SSCC_DL);

        assertThat(response.getIdentifierType()).isEqualTo("SSCC");

        boolean hasPalletLink = response.getLinkSets().stream()
                .flatMap(ls -> ls.getLinks().stream())
                .anyMatch(l -> l.getHref() != null && l.getHref().contains("/inventory/pallet/"));
        assertThat(hasPalletLink).isTrue();
    }

    // ─────────────────────────────────────────────
    // resolve — invalid URI
    // ─────────────────────────────────────────────

    @Test
    void resolve_invalidUri_throwsParseException() {
        assertThatThrownBy(() -> resolverService.resolve("not-a-valid-digital-link"))
                .isInstanceOf(DigitalLinkParseException.class);
    }

    // ─────────────────────────────────────────────
    // getEventHistory
    // ─────────────────────────────────────────────

    @Test
    void getEventHistory_sgtin_queriesRepositoryWithEpcUrn() {
        EpcisEventEntity event = EpcisEventEntity.builder()
                .eventType("ObjectEvent")
                .payload("{}")
                .build();

        when(eventRepository.search(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(SGTIN_URN), isNull(), isNull(), isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        List<EpcisEventEntity> history = resolverService.getEventHistory(parser.parse(SGTIN_DL));

        assertThat(history).hasSize(1);
    }

    // ─────────────────────────────────────────────
    // getCurrentLocation
    // ─────────────────────────────────────────────

    @Test
    void getCurrentLocation_knownEpc_returnsState() {
        EpcState state = EpcState.builder()
                .epcUrn(SGTIN_URN)
                .currentStatus("in_transit")
                .lastEventTime(OffsetDateTime.now())
                .build();
        when(inventoryQueryService.getEpcState(SGTIN_URN)).thenReturn(state);

        Optional<EpcState> result = resolverService.getCurrentLocation(parser.parse(SGTIN_DL));

        assertThat(result).isPresent();
        assertThat(result.get().getEpcUrn()).isEqualTo(SGTIN_URN);
    }

    @Test
    void getCurrentLocation_unknownEpc_returnsEmpty() {
        when(inventoryQueryService.getEpcState(SGTIN_URN))
                .thenThrow(new EpcNotFoundException(SGTIN_URN));

        Optional<EpcState> result = resolverService.getCurrentLocation(parser.parse(SGTIN_DL));

        assertThat(result).isEmpty();
    }
}
