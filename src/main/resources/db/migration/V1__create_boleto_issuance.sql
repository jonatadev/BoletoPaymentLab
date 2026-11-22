CREATE TABLE boleto (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(120) NOT NULL,
    amount NUMERIC(8, 2) NOT NULL CHECK (amount BETWEEN 0.01 AND 999999.99),
    due_date DATE NOT NULL
);

CREATE TABLE idempotent_issuance (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(120) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    request_key VARCHAR(160) NOT NULL,
    boleto_id UUID NOT NULL UNIQUE REFERENCES boleto(id),
    CONSTRAINT uq_issuance_scope UNIQUE (customer_id, operation, request_key)
);
