# Observability as Code

This directory is the versioned observability configuration for BoletoPaymentLab.

## Contents

- `prometheus/` - PromQL query library for JVM, HTTP, HikariCP, Kafka, and business metrics.
- `grafana/dashboards/application-overview.json` - application health and runtime dashboard.
- `grafana/dashboards/business-metrics.json` - boleto and Outbox business dashboard.
- `alloy/config.alloy` - the existing Grafana Alloy scrape and remote-write configuration.
- `alloy/traces.alloy` - the optional OTLP pipeline for Jaeger and Tempo.
- `docs/metrics.md` - metric inventory and label reference.

The dashboards use the Grafana variable `$datasource` and require Prometheus data
with the `job="boleto-payment-lab"` label. The Alloy configuration remains the
source of the scrape target and remote-write settings.

## Import dashboards into Grafana Cloud

1. Open Grafana Cloud and select **Connections > Data sources**.
2. Confirm that the Prometheus data source to use is available.
3. Open **Dashboards > New > Import**.
4. Upload `grafana/dashboards/application-overview.json`.
5. At import time, map the `$datasource` variable to the Prometheus data source.
6. Repeat for `grafana/dashboards/business-metrics.json`.
7. Verify the dashboards use the stable UIDs:
   - `bpl-app-overview`
   - `bpl-business-metrics`

The JSON files can be imported through Grafana provisioning or the Grafana API.
Do not embed stack IDs, passwords, client secrets, or metric tokens in the JSON.

## Configure Grafana Cloud Git Sync

When Git Sync is available for the Grafana organization:

1. Open **Administration > Provisioning > Git Sync**.
2. Configure read access to this repository or expose the dashboard JSON files
   through the repository integration.
3. Select `observability/grafana/dashboards` as the dashboard path.
4. Choose a dedicated Grafana folder for BoletoPaymentLab dashboards.
5. Enable the import mode that preserves the stable UIDs.
6. Configure the Prometheus data source by UID or name through the Grafana
   environment, rather than committing credentials or stack-specific values.

Git Sync should track JSON changes and not write generated data or secrets back
to the repository.

## Alloy

`observability/alloy/config.alloy` preserves the existing scrape and remote-write
behavior. The Docker Compose volume in `compose.alloy.yaml` points to this
versioned file. The Tempo overlay loads the same file together with
`traces.alloy`. Credentials continue to be supplied through environment
variables from `.env`, which is ignored by Git.

## Validation

The scrape target can be checked with:

```promql
up{job="boleto-payment-lab"}
```

Before using a business panel, confirm the relevant series exists at
`/actuator/prometheus`. Counter families are registered lazily and may not
appear until their first occurrence.
