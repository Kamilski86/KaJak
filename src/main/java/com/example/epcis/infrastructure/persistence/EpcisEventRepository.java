package com.example.epcis.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data Repository für EPCIS Events.
 * Alle CRUD-Operationen werden automatisch generiert.
 */
@Repository
public interface EpcisEventRepository extends JpaRepository<EpcisEventEntity, Long> {
}
