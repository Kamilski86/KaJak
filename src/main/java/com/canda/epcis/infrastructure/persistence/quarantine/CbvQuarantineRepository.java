package com.canda.epcis.infrastructure.persistence.quarantine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CbvQuarantineRepository extends JpaRepository<CbvQuarantineEntity, Long> {

    List<CbvQuarantineEntity> findByResolved(boolean resolved);

    List<CbvQuarantineEntity> findByResolvedAndQuarantinedAtAfter(
            boolean resolved, OffsetDateTime from);

    List<CbvQuarantineEntity> findByReasonCode(String reasonCode);
}
