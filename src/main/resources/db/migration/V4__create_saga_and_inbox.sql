CREATE TABLE saga_instances (
    id UUID PRIMARY KEY,
    ordem_servico_id UUID NOT NULL UNIQUE,
    status VARCHAR(64) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saga_instances_ordens_servico
        FOREIGN KEY (ordem_servico_id) REFERENCES ordens_servico (id),
    CONSTRAINT ck_saga_instances_status CHECK (
        status IN (
            'STARTED',
            'WAITING_DIAGNOSTIC',
            'WAITING_QUOTE',
            'WAITING_APPROVAL',
            'WAITING_REPAIR',
            'WAITING_PAYMENT',
            'COMPENSATING',
            'COMPLETED',
            'COMPENSATED',
            'FAILED'
        )
    ),
    CONSTRAINT ck_saga_instances_current_step CHECK (
        current_step IN (
            'DIAGNOSTIC',
            'QUOTE',
            'APPROVAL',
            'REPAIR',
            'PAYMENT',
            'COMPENSATION',
            'DONE'
        )
    )
);

CREATE INDEX idx_saga_instances_ordem_servico_id ON saga_instances (ordem_servico_id);
CREATE INDEX idx_saga_instances_status ON saga_instances (status);
CREATE INDEX idx_saga_instances_correlation_id ON saga_instances (correlation_id);

CREATE TABLE inbox_messages (
    event_id UUID PRIMARY KEY,
    saga_id UUID,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT fk_inbox_messages_saga_instances
        FOREIGN KEY (saga_id) REFERENCES saga_instances (id),
    CONSTRAINT ck_inbox_messages_status CHECK (
        status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'REJECTED')
    )
);

CREATE INDEX idx_inbox_messages_saga_id ON inbox_messages (saga_id);
CREATE INDEX idx_inbox_messages_status ON inbox_messages (status, received_at);
CREATE INDEX idx_inbox_messages_event_type ON inbox_messages (event_type);
