package com.canda.epcis.infrastructure.persistence.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    List<OutboxEntity> findByStatusAndTargetSystemOrderByCreatedAtAsc(String status, String targetSystem);

    List<OutboxEntity> findByStatusOrderByCreatedAtAsc(String status);

    /** Idempotenz — wurde diese Nachricht bereits gesendet? */
    boolean existsByMessageId(String messageId);

    /** Cleanup — gesendete Messages älter als X Tage */
    void deleteByStatusAndSentAtBefore(String status, OffsetDateTime before);

    /** Stats — Anzahl pro targetSystem + status für /ops/outbox/stats */
    @Query("SELECT o.targetSystem, o.status, COUNT(o) FROM OutboxEntity o GROUP BY o.targetSystem, o.status ORDER BY o.targetSystem, o.status")
    List<Object[]> countGroupedByTargetAndStatus();
}
