-- Legacy-upgrade tests may retain the namespace while resetting Flyway history.
-- The views below remain versioned and fail normally if their own definitions collide.
CREATE SCHEMA IF NOT EXISTS analytics;
REVOKE ALL ON SCHEMA analytics FROM PUBLIC;

CREATE OR REPLACE VIEW analytics.boleto_daily AS
SELECT issued_at::date AS issued_date, COUNT(*) AS boleto_count, COALESCE(SUM(amount), 0) AS total_amount
FROM boleto
GROUP BY issued_at::date;

CREATE OR REPLACE VIEW analytics.boleto_status AS
SELECT status, COUNT(*) AS boleto_count, COALESCE(SUM(amount), 0) AS total_amount
FROM boleto
GROUP BY status;

CREATE OR REPLACE VIEW analytics.outbox_summary AS
SELECT COUNT(*) AS total_events,
       COUNT(*) FILTER (WHERE published_at IS NULL) AS pending_events,
       COUNT(*) FILTER (WHERE published_at IS NOT NULL) AS published_events,
       MIN(occurred_at) FILTER (WHERE published_at IS NULL) AS oldest_pending_at
FROM boleto_outbox;

CREATE OR REPLACE VIEW analytics.processing_summary AS
SELECT COUNT(*) AS processed_operations
FROM processed_boleto_event;

REVOKE ALL ON ALL TABLES IN SCHEMA analytics FROM PUBLIC;
