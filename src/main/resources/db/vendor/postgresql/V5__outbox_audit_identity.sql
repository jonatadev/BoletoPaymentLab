SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE boleto_audit
    ADD CONSTRAINT uq_boleto_audit_event_identity
    UNIQUE (event_id, boleto_id, aggregate_version);

ALTER TABLE boleto_outbox
    ADD CONSTRAINT fk_boleto_outbox_audit_identity
    FOREIGN KEY (event_id, aggregate_id, aggregate_version)
    REFERENCES boleto_audit (event_id, boleto_id, aggregate_version)
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;
