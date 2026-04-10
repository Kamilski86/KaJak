-- V1: Initial EPCIS event store schema
-- Captures the state produced previously by ddl-auto: update.

CREATE TABLE IF NOT EXISTS epcis_event (
    id          BIGSERIAL       PRIMARY KEY,
    event_type  VARCHAR(50)     NOT NULL,
    event_time  TIMESTAMPTZ,
    payload     JSONB           NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Indexed search paths used by the native JPQL query in EpcisEventRepository
CREATE INDEX IF NOT EXISTS idx_epcis_event_type        ON epcis_event (event_type);
CREATE INDEX IF NOT EXISTS idx_epcis_event_time        ON epcis_event (event_time);
CREATE INDEX IF NOT EXISTS idx_epcis_event_payload_gin ON epcis_event USING gin (payload);
