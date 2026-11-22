CREATE TABLE processed_boleto_event (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
