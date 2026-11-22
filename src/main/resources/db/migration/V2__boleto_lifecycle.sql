ALTER TABLE boleto ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'EMITIDO';
ALTER TABLE boleto ADD COLUMN issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE boleto ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE boleto ADD CONSTRAINT ck_boleto_status
    CHECK (status IN ('EMITIDO', 'PAGO', 'VENCIDO', 'CANCELADO'));
CREATE INDEX ix_boleto_issued_at ON boleto (issued_at, id);
CREATE INDEX ix_boleto_customer_period ON boleto (customer_id, issued_at, id);
CREATE INDEX ix_boleto_status_due ON boleto (status, due_date, id);
