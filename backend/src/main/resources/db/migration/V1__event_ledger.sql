CREATE TABLE IF NOT EXISTS sentinel_event (
    id              BIGSERIAL PRIMARY KEY,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    category        VARCHAR(64) NOT NULL,
    severity        VARCHAR(16) NOT NULL DEFAULT 'INFO',
    source          VARCHAR(128) NOT NULL,
    summary         VARCHAR(512) NOT NULL,
    detail_json     TEXT,
    action_taken    VARCHAR(64) NOT NULL DEFAULT 'observe_only'
);

CREATE INDEX IF NOT EXISTS idx_sentinel_event_occurred ON sentinel_event (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_sentinel_event_category ON sentinel_event (category);
