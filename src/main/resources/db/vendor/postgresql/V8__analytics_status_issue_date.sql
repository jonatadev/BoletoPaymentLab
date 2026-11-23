CREATE OR REPLACE VIEW analytics.boleto_status AS
SELECT status,
       COUNT(*) AS boleto_count,
       COALESCE(SUM(amount), 0) AS total_amount,
       issued_at::date AS issued_date
FROM boleto
GROUP BY status, issued_at::date;
