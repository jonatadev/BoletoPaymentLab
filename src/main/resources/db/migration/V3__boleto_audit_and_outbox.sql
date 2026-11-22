CREATE TABLE boleto_audit (
    event_id UUID PRIMARY KEY,
    boleto_id UUID NOT NULL REFERENCES boleto(id),
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(8) NOT NULL CHECK (source IN ('API', 'SYSTEM')),
    previous_status VARCHAR(16),
    new_status VARCHAR(16) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    CONSTRAINT uq_boleto_audit_version UNIQUE (boleto_id, aggregate_version)
);

CREATE TABLE boleto_outbox (
    event_id UUID PRIMARY KEY REFERENCES boleto_audit(event_id),
    aggregate_id UUID NOT NULL REFERENCES boleto(id),
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    aggregate_version BIGINT NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    traceparent VARCHAR(55),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_boleto_outbox_version UNIQUE (aggregate_id, aggregate_version)
);
CREATE INDEX ix_outbox_poll ON boleto_outbox (published_at, next_attempt_at, event_id);
