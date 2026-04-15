package com.canda.epcis.infrastructure.persistence.cbv;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CbvVocabularyRepository extends JpaRepository<CbvVocabularyEntity, Long> {

    Optional<CbvVocabularyEntity> findByVocabularyTypeAndPayload(
            String vocabularyType, String payload);

    boolean existsByVocabularyTypeAndUrnUri(String vocabularyType, String urnUri);

    boolean existsByVocabularyTypeAndHttpsUri(String vocabularyType, String httpsUri);

    List<CbvVocabularyEntity> findByVocabularyType(String vocabularyType);

    List<CbvVocabularyEntity> findByVocabularyTypeAndDeprecatedFalse(String vocabularyType);
}
