package com.canda.epcis.infrastructure.persistence.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpcStateRepository extends JpaRepository<EpcStateEntity, Long> {

    Optional<EpcStateEntity> findByEpcUrn(String epcUrn);

    /** Alle SGTINs an einem bestimmten Standort (exakter Match). */
    List<EpcStateEntity> findByBizLocation(String bizLocation);

    /** Alle SGTINs die einer bestimmten SSCC zugeordnet sind. */
    List<EpcStateEntity> findBySscc(String sscc);

    /** Anzahl SGTINs an einem bestimmten Standort. */
    long countByBizLocation(String bizLocation);

    /**
     * Suche nach GTIN-Prefix (alle Serialnummern einer GTIN).
     * GTIN-Prefix = SGTIN URN ohne Seriennummer, z.B. "urn:epc:id:sgtin:4056019.010532."
     */
    @Query("SELECT e FROM EpcStateEntity e WHERE e.epcUrn LIKE :gtinPrefix%")
    List<EpcStateEntity> findByGtinPrefix(@Param("gtinPrefix") String gtinPrefix);

    /** Idempotenz-Prüfung: wurde dieses Event für diese EPC bereits verarbeitet? */
    boolean existsByEpcUrnAndLastEventId(String epcUrn, String eventId);

    /**
     * REQ301: Alle SGTINs an einem Standort (Prefix-Suche) mit bestimmten Dispositionen.
     * Wird für /inventory/stock?availableOnly=true verwendet.
     */
    @Query("SELECT e FROM EpcStateEntity e " +
           "WHERE e.bizLocation LIKE :glnPrefix% " +
           "AND e.currentStatus IN :dispositions")
    List<EpcStateEntity> findByBizLocationPrefixAndCurrentStatusIn(
            @Param("glnPrefix") String glnPrefix,
            @Param("dispositions") List<String> dispositions);

    /**
     * REQ301: Alle verfügbaren SGTINs — optional gefiltert nach Standort.
     * Wird für /inventory/available-quantity verwendet (GSPM-Format).
     */
    @Query("SELECT e FROM EpcStateEntity e " +
           "WHERE e.currentStatus IN :dispositions " +
           "AND (:gln IS NULL OR e.bizLocation LIKE CONCAT(:gln, '%'))")
    List<EpcStateEntity> findAvailableMerchandise(
            @Param("dispositions") List<String> dispositions,
            @Param("gln") String gln);
}
