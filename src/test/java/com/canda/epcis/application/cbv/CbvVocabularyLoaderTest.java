package com.canda.epcis.application.cbv;

import com.canda.epcis.domain.model.cbv.CbvVocabularyType;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyEntity;
import com.canda.epcis.infrastructure.persistence.cbv.CbvVocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CbvVocabularyLoaderTest {

    @Mock
    CbvVocabularyRepository repository;

    CbvVocabularyLoader loader;

    @BeforeEach
    void setUp() {
        loader = new CbvVocabularyLoader(repository);
    }

    @Test
    void loadIfEmpty_whenTableEmpty_savesAllEntries() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<CbvVocabularyEntity> saved = captor.getValue();
        assertThat(saved).hasSize(86); // 40 + 31 + 12 + 3
    }

    @Test
    void loadIfEmpty_whenTableAlreadyFilled_skipsLoad() {
        when(repository.count()).thenReturn(86L);

        loader.loadIfEmpty();

        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loadIfEmpty_bizStepEntries_allFortyPresent() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<CbvVocabularyEntity> bizSteps = captor.getValue().stream()
                .filter(e -> CbvVocabularyType.BIZ_STEP.name().equals(e.getVocabularyType()))
                .toList();
        assertThat(bizSteps).hasSize(40);
    }

    @Test
    void loadIfEmpty_dispositionEntries_thirtyOnePresent() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<CbvVocabularyEntity> dispositions = captor.getValue().stream()
                .filter(e -> CbvVocabularyType.DISPOSITION.name().equals(e.getVocabularyType()))
                .toList();
        assertThat(dispositions).hasSize(31);
    }

    @Test
    void loadIfEmpty_bizTransactionTypeEntries_twelvePresent() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<CbvVocabularyEntity> btts = captor.getValue().stream()
                .filter(e -> CbvVocabularyType.BIZ_TRANSACTION_TYPE.name().equals(e.getVocabularyType()))
                .toList();
        assertThat(btts).hasSize(12);
    }

    @Test
    void loadIfEmpty_sourceDestTypeEntries_threePresent() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<CbvVocabularyEntity> sdts = captor.getValue().stream()
                .filter(e -> CbvVocabularyType.SOURCE_DEST_TYPE.name().equals(e.getVocabularyType()))
                .toList();
        assertThat(sdts).hasSize(3);
    }

    @Test
    void loadIfEmpty_eachEntry_hasBothUrnAndHttpsUri() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        captor.getValue().forEach(e -> {
            assertThat(e.getUrnUri()).isNotBlank().startsWith("urn:epcglobal:cbv:");
            assertThat(e.getHttpsUri()).isNotBlank().startsWith("https://ref.gs1.org/cbv/");
            assertThat(e.getPayload()).isNotBlank();
            assertThat(e.getVocabularyType()).isNotBlank();
        });
    }

    @Test
    void loadIfEmpty_shippingBizStep_hasCorrectUris() {
        when(repository.count()).thenReturn(0L);

        loader.loadIfEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CbvVocabularyEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        CbvVocabularyEntity shipping = captor.getValue().stream()
                .filter(e -> "shipping".equals(e.getPayload())
                        && CbvVocabularyType.BIZ_STEP.name().equals(e.getVocabularyType()))
                .findFirst()
                .orElseThrow();

        assertThat(shipping.getUrnUri()).isEqualTo("urn:epcglobal:cbv:bizstep:shipping");
        assertThat(shipping.getHttpsUri()).isEqualTo("https://ref.gs1.org/cbv/BizStep-shipping");
        assertThat(shipping.isDeprecated()).isFalse();
    }
}
