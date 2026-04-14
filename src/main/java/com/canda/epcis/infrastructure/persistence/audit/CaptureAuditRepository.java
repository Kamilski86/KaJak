package com.canda.epcis.infrastructure.persistence.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CaptureAuditRepository extends JpaRepository<CaptureAuditEntity, Long> {

    List<CaptureAuditEntity> findBySourceIdOrderByReceivedAtDesc(String sourceId);

    List<CaptureAuditEntity> findByReceivedAtBetween(OffsetDateTime from, OffsetDateTime to);

    long countByReceivedAtBetween(OffsetDateTime from, OffsetDateTime to);
}
