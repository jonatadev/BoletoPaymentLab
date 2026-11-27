# BoletoPaymentLab Metrics

The current `/actuator/prometheus` endpoint was inspected on the running
application before this inventory was created. Series below are listed only
when they are confirmed in the endpoint or are directly defined in the
instrumentation code.

## Confirmed in the running endpoint

| Family | Exported names | Primary labels |
| --- | --- | --- |
| JVM | `jvm_memory_used_bytes`, `jvm_memory_max_bytes`, `jvm_memory_committed_bytes`, `jvm_threads_live_threads`, `jvm_gc_pause_seconds_bucket` | `area`, `id`, `pool`, `state` as applicable |
| Process and CPU | `process_cpu_usage`, `process_uptime_seconds`, `system_cpu_usage`, `system_cpu_count` | none normally required |
| HTTP | `http_server_requests_seconds_count`, `http_server_requests_seconds_sum`, `http_server_requests_seconds_bucket` | `exception`, `method`, `outcome`, `status`, `uri` |
| HikariCP | `hikaricp_connections_active`, `hikaricp_connections_idle`, `hikaricp_connections_pending`, `hikaricp_connections_max`, `hikaricp_connections_acquire_seconds_bucket` | `pool` |
| Kafka | `kafka_consumer_connection_count`, `kafka_consumer_fetch_manager_records_lag`, `kafka_consumer_coordinator_assigned_partitions`, `kafka_consumer_coordinator_commit_total` | `client_id`, `kafka_version`, `spring_id`; metric-specific Kafka labels also apply |
| Outbox | `bpl_outbox_pending` | no application-specific labels |

## Instrumented in code; emitted after the first occurrence

Counter and observation families are registered by use. Micrometer exports
them only after an occurrence, so an idle application may not show them in
`/actuator/prometheus`.

| Micrometer instrument | Prometheus export | Labels |
| --- | --- | --- |
| `bpl.boletos.issued` | `bpl_boletos_issued_total` | none added by the counter |
| `bpl.boletos.paid` | `bpl_boletos_paid_total` | none added by the counter |
| `bpl.boletos.cancelled` | `bpl_boletos_cancelled_total` | none added by the counter |
| `bpl.issuance` | `bpl_issuance_seconds_count`, `bpl_issuance_seconds_sum`, `bpl_issuance_seconds_bucket`, `bpl_issuance_seconds_max` | `operation`, `outcome` |
| `bpl.pdf.render` | `bpl_pdf_render_seconds_count`, `bpl_pdf_render_seconds_sum`, `bpl_pdf_render_seconds_bucket`, `bpl_pdf_render_seconds_max` | `operation`, `outcome` |
| `bpl.outbox.published` | `bpl_outbox_published_total` | none added by the counter |
| `bpl.outbox.errors` | `bpl_outbox_errors_total` | none added by the counter |
| `bpl.idempotency.conflicts` | `bpl_idempotency_conflicts_total` | none added by the counter |
| `bpl.api.errors` | `bpl_api_errors_total` | none added by the counter |
| `bpl.events.duplicates` | `bpl_events_duplicates_total` | none added by the counter |

For issuance, payment, cancellation, PDF rendering, Outbox publication and
Outbox errors, counters and observations are incremented after a successful
transaction commit where `countAfterCommit` is used.

## Scrape labels

Grafana Alloy sets `job="boleto-payment-lab"` and scrapes
`/actuator/prometheus` every 30 seconds. If a different scrape configuration is
used, keep the dashboard variable and update the query filter to the real job
label rather than adding a second, invented job value.
