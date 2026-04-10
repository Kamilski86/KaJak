package com.canda.epcis.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface EpcisEventRepository extends JpaRepository<EpcisEventEntity, Long> {

    @Query(value = """
        SELECT * FROM epcis_event e
        WHERE
            (cast(:type AS text)        IS NULL OR e.event_type = :type)
        AND (cast(:action AS text)      IS NULL OR e.payload::jsonb ->> 'action'         = :action)
        AND (cast(:bizStep AS text)     IS NULL OR e.payload::jsonb ->> 'bizStep'        = :bizStep)
        AND (cast(:disposition AS text) IS NULL OR e.payload::jsonb ->> 'disposition'    = :disposition)
        AND (cast(:readPoint AS text)   IS NULL OR e.payload::jsonb -> 'readPoint'     ->> 'id' = :readPoint)
        AND (cast(:bizLocation AS text) IS NULL OR e.payload::jsonb -> 'bizLocation'   ->> 'id' = :bizLocation)
        AND (cast(:parentId AS text)    IS NULL OR e.payload::jsonb ->> 'parentID'       = :parentId)
        AND (cast(:epc AS text)         IS NULL OR e.payload::jsonb -> 'epcList'        @> jsonb_build_array(cast(:epc AS text))
                                                OR e.payload::jsonb -> 'childEPCs'      @> jsonb_build_array(cast(:epc AS text)))
        AND (cast(:gln AS text)         IS NULL OR e.payload::jsonb -> 'readPoint'     ->> 'id' = :gln
                                                OR e.payload::jsonb -> 'bizLocation'   ->> 'id' = :gln
                                                OR e.payload::jsonb -> 'extension' -> 'destinationList' ->> 'destination' = :gln)
        AND (cast(:from AS timestamptz) IS NULL OR e.event_time >= cast(:from AS timestamptz))
        AND (cast(:to AS timestamptz)   IS NULL OR e.event_time <= cast(:to AS timestamptz))
        ORDER BY e.event_time ASC, e.id ASC
        """,
            countQuery = """
        SELECT count(*) FROM epcis_event e
        WHERE
            (cast(:type AS text)        IS NULL OR e.event_type = :type)
        AND (cast(:action AS text)      IS NULL OR e.payload::jsonb ->> 'action'         = :action)
        AND (cast(:bizStep AS text)     IS NULL OR e.payload::jsonb ->> 'bizStep'        = :bizStep)
        AND (cast(:disposition AS text) IS NULL OR e.payload::jsonb ->> 'disposition'    = :disposition)
        AND (cast(:readPoint AS text)   IS NULL OR e.payload::jsonb -> 'readPoint'     ->> 'id' = :readPoint)
        AND (cast(:bizLocation AS text) IS NULL OR e.payload::jsonb -> 'bizLocation'   ->> 'id' = :bizLocation)
        AND (cast(:parentId AS text)    IS NULL OR e.payload::jsonb ->> 'parentID'       = :parentId)
        AND (cast(:epc AS text)         IS NULL OR e.payload::jsonb -> 'epcList'        @> jsonb_build_array(cast(:epc AS text))
                                                OR e.payload::jsonb -> 'childEPCs'      @> jsonb_build_array(cast(:epc AS text)))
        AND (cast(:gln AS text)         IS NULL OR e.payload::jsonb -> 'readPoint'     ->> 'id' = :gln
                                                OR e.payload::jsonb -> 'bizLocation'   ->> 'id' = :gln
                                                OR e.payload::jsonb -> 'extension' -> 'destinationList' ->> 'destination' = :gln)
        AND (cast(:from AS timestamptz) IS NULL OR e.event_time >= cast(:from AS timestamptz))
        AND (cast(:to AS timestamptz)   IS NULL OR e.event_time <= cast(:to AS timestamptz))
        """,
            nativeQuery = true)
    Page<EpcisEventEntity> search(
            @Param("type")        String type,
            @Param("action")      String action,
            @Param("bizStep")     String bizStep,
            @Param("disposition") String disposition,
            @Param("readPoint")   String readPoint,
            @Param("bizLocation") String bizLocation,
            @Param("parentId")    String parentId,
            @Param("epc")         String epc,
            @Param("gln")         String gln,
            @Param("from")        OffsetDateTime from,
            @Param("to")          OffsetDateTime to,
            Pageable pageable
    );

    /** H2-compatible fallback used by the local profile (no PostgreSQL JSON operators). */
    @Query("SELECT e FROM EpcisEventEntity e WHERE " +
           "(:type IS NULL OR e.eventType = :type) AND " +
           "(:from IS NULL OR e.eventTime >= :from) AND " +
           "(:to   IS NULL OR e.eventTime <= :to) " +
           "ORDER BY e.eventTime ASC, e.id ASC")
    Page<EpcisEventEntity> searchBasic(
            @Param("type") String type,
            @Param("from") OffsetDateTime from,
            @Param("to")   OffsetDateTime to,
            Pageable pageable
    );
}
