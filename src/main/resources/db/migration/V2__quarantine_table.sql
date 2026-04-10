-- V2: Quarantine table for events that cannot be automatically migrated.
-- Events are routed here when: event type is unsupported, mapping is lossy,
-- CBV validation fails, or any other condition that prevents safe conversion.

CREATE TABLE epcis_quarantine (
    id              BIGSERIAL       PRIMARY KEY,
    reason          VARCHAR(500)    NOT NULL,
    error_code      VARCHAR(100),
    raw_fragment    TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_epcis_quarantine_created_at  ON epcis_quarantine (created_at);
CREATE INDEX idx_epcis_quarantine_error_code  ON epcis_quarantine (error_code);
