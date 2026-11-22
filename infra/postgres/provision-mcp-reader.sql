-- Run once as a PostgreSQL administrator after Flyway V7 succeeds.
-- Replace the placeholder before execution. Do not store a production password here.
CREATE ROLE boleto_mcp_reader LOGIN PASSWORD 'replace-before-execution' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

GRANT CONNECT ON DATABASE boleto_lab TO boleto_mcp_reader;
GRANT USAGE ON SCHEMA analytics TO boleto_mcp_reader;
GRANT SELECT ON analytics.boleto_daily TO boleto_mcp_reader;
GRANT SELECT ON analytics.boleto_status TO boleto_mcp_reader;
GRANT SELECT ON analytics.outbox_summary TO boleto_mcp_reader;
GRANT SELECT ON analytics.processing_summary TO boleto_mcp_reader;

ALTER ROLE boleto_mcp_reader SET default_transaction_read_only = on;
ALTER ROLE boleto_mcp_reader SET statement_timeout = '5s';
