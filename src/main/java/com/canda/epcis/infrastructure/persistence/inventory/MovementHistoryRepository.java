package com.canda.epcis.infrastructure.persistence.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface MovementHistoryRepository extends JpaRepository<MovementHistoryEntity, Long> {

    /** Vollständige Historie einer EPC, neueste zuerst. */
    List<MovementHistoryEntity> findByEpcUrnOrderByEventTimeDesc(String epcUrn);

    /** Historie einer EPC gefiltert nach Zeitraum, neueste zuerst. */
    List<MovementHistoryEntity> findByEpcUrnAndEventTimeBetweenOrderByEventTimeDesc(
            String epcUrn, OffsetDateTime from, OffsetDateTime to);

    /**
     * Idempotenz-Check: wurde dieses Event für diese EPC bereits in der History gespeichert?
     */
    boolean existsByEpcUrnAndEventId(String epcUrn, String eventId);

    /** Alle History-Einträge an einem Standort in einem Zeitraum. */
    List<MovementHistoryEntity> findByBizLocationAndEventTimeBetween(
            String bizLocation, OffsetDateTime from, OffsetDateTime to);
}
