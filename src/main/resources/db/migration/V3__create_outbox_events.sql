CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    locked_until TIMESTAMPTZ,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_outbox_events_retry
    ON outbox_events (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_locked_until
    ON outbox_events (locked_until)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);
