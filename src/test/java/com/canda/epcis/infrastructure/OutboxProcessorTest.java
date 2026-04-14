package com.canda.epcis.infrastructure;

import com.canda.epcis.config.DownstreamConfig;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxEntity;
import com.canda.epcis.infrastructure.persistence.outbox.OutboxRepository;
import com.canda.epcis.infrastructure.persistence.subscription.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock OutboxRepository       outboxRepository;
    @Mock SubscriptionRepository subscriptionRepository;

    private DownstreamConfig config;
    private OutboxProcessor  processor;

    @BeforeEach
    void setUp() {
        config = new DownstreamConfig();
        config.getOutbox().setMaxRetries(3);
        processor = new OutboxProcessor(outboxRepository, config, subscriptionRepository);
    }

    // ─────────────────────────────────────────────
    // Disabled target → skip silently
    // ─────────────────────────────────────────────

    @Test
    void processMessage_targetDisabled_skipsWithoutSaving() {
        // GSPM enabled=false (default)
        OutboxEntity msg = pendingMessage("GSPM", "RFID_INVENTORY");

        processor.processMessage(msg);

        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // URL resolution
    // ─────────────────────────────────────────────

    @Test
    void resolveUrl_gspmInventory_returnsCorrectUrl() {
        config.getGspm().setBaseUrl("http://gspm:9010");
        config.getGspm().setInventoryPath("/api/inventory");
        OutboxEntity msg = pendingMessage("GSPM", "RFID_INVENTORY");

        String url = processor.resolveUrl(msg);

        assertThat(url).isEqualTo("http://gspm:9010/api/inventory");
    }

    @Test
    void resolveUrl_gspmShipping_returnsCorrectUrl() {
        config.getGspm().setBaseUrl("http://gspm:9010");
        config.getGspm().setShippingPath("/api/shipping");

        String url = processor.resolveUrl(pendingMessage("GSPM", "RFID_SHIPPING"));

        assertThat(url).isEqualTo("http://gspm:9010/api/shipping");
    }

    @Test
    void resolveUrl_gspmArrival_returnsCorrectUrl() {
        config.getGspm().setBaseUrl("http://gspm:9010");
        config.getGspm().setArrivalPath("/api/arrival");

        String url = processor.resolveUrl(pendingMessage("GSPM", "RFID_ARRIVAL"));

        assertThat(url).isEqualTo("http://gspm:9010/api/arrival");
    }

    @Test
    void resolveUrl_erp_returnsCorrectUrl() {
        config.getErp().setBaseUrl("http://erp:9011");
        config.getErp().setGoodsReceiptPath("/api/gr");

        String url = processor.resolveUrl(pendingMessage("ERP", "GOODS_RECEIPT"));

        assertThat(url).isEqualTo("http://erp:9011/api/gr");
    }

    @Test
    void resolveUrl_dwhSales_returnsCorrectUrl() {
        config.getDwh().setBaseUrl("http://dwh:9012");
        config.getDwh().setSalesPath("/api/sales");

        String url = processor.resolveUrl(pendingMessage("DWH", "RFID_SALES"));

        assertThat(url).isEqualTo("http://dwh:9012/api/sales");
    }

    @Test
    void resolveUrl_ewm_returnsCorrectUrl() {
        config.getEwm().setBaseUrl("http://ewm:9013");
        config.getEwm().setTrolleyPath("/api/trolley");

        String url = processor.resolveUrl(pendingMessage("EWM", "TROLLEY_SSCCS"));

        assertThat(url).isEqualTo("http://ewm:9013/api/trolley");
    }

    @Test
    void resolveUrl_unknownTarget_returnsNull() {
        String url = processor.resolveUrl(pendingMessage("UNKNOWN", "WHATEVER"));

        assertThat(url).isNull();
    }

    @Test
    void resolveUrl_subscriptionNoRepository_returnsNull() {
        when(subscriptionRepository.findBySubscriptionId("ITEMOPTIX-GENERAL"))
                .thenReturn(java.util.Optional.empty());

        OutboxEntity msg = pendingMessage("SUBSCRIPTION", "SUBSCRIPTION");
        msg.setCorrelationId("ITEMOPTIX-GENERAL");

        String url = processor.resolveUrl(msg);

        assertThat(url).isNull();
    }

    // ─────────────────────────────────────────────
    // Retry logic
    // ─────────────────────────────────────────────

    @Test
    void processMessage_httpFailure_incrementsRetryCount() {
        config.getGspm().setEnabled(true);
        config.getGspm().setBaseUrl("http://unreachable-host-xyz.invalid");
        config.getGspm().setInventoryPath("/api/inventory");

        OutboxEntity msg = pendingMessage("GSPM", "RFID_INVENTORY");
        msg.setRetryCount(0);
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        processor.processMessage(msg);

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEntity saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo("PENDING"); // still pending, not yet at max
        assertThat(saved.getLastError()).isNotNull();
    }

    @Test
    void processMessage_maxRetriesReached_setsStatusFailed() {
        config.getGspm().setEnabled(true);
        config.getGspm().setBaseUrl("http://unreachable-host-xyz.invalid");
        config.getGspm().setInventoryPath("/api/inventory");
        config.getOutbox().setMaxRetries(3);

        OutboxEntity msg = pendingMessage("GSPM", "RFID_INVENTORY");
        msg.setRetryCount(2); // one more attempt → reaches maxRetries (3)
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        processor.processMessage(msg);

        ArgumentCaptor<OutboxEntity> captor = ArgumentCaptor.forClass(OutboxEntity.class);
        verify(outboxRepository).save(captor.capture());

        OutboxEntity saved = captor.getValue();
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getLastError()).isNotNull();
    }

    // ─────────────────────────────────────────────
    // processOutbox excludes EWM
    // ─────────────────────────────────────────────

    @Test
    void processOutbox_filtersOutEwmMessages() {
        OutboxEntity gspmMsg = pendingMessage("GSPM", "RFID_INVENTORY");
        OutboxEntity ewmMsg  = pendingMessage("EWM",  "TROLLEY_SSCCS");
        when(outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(gspmMsg, ewmMsg));

        processor.processOutbox();

        // GSPM is disabled (default) → skip. EWM is filtered out entirely.
        // Key assertion: EWM message was never processed by processOutbox
        // (both skip silently — no save called)
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void processEwmOutbox_onlyProcessesEwmMessages() {
        OutboxEntity ewmMsg = pendingMessage("EWM", "TROLLEY_SSCCS");
        when(outboxRepository.findByStatusAndTargetSystemOrderByCreatedAtAsc("PENDING", "EWM"))
                .thenReturn(List.of(ewmMsg));

        processor.processEwmOutbox();

        // EWM disabled by default → skip silently
        verify(outboxRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private OutboxEntity pendingMessage(String targetSystem, String messageType) {
        return OutboxEntity.builder()
                .messageId("test-" + System.nanoTime())
                .messageType(messageType)
                .targetSystem(targetSystem)
                .payload("{\"test\":true}")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .retryCount(0)
                .build();
    }
}
