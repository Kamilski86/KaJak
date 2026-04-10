package com.canda.epcis.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuarantineRepository extends JpaRepository<QuarantineEntity, Long> {
    Page<QuarantineEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
