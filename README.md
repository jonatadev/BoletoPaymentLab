# Boleto Payment Lab

[![CI](https://github.com/jonatadev/BoletoPaymentLab/actions/workflows/coverage.yaml/badge.svg?branch=main)](https://github.com/jonatadev/BoletoPaymentLab/actions/workflows/coverage.yaml)
[![Codecov](https://codecov.io/gh/jonatadev/BoletoPaymentLab/branch/main/graph/badge.svg)](https://codecov.io/gh/jonatadev/BoletoPaymentLab)
![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F)

Local educational lab for synthetic boleto records. The
[MVP specification](specs/usecases/UC-BPL-001-emitir-boleto.md) is under
review. The first implementation provides local issuance, lookup and
on-demand educational PDF rendering.

## Technical documentation portal

The [Docusaurus portal](docs-site/README.md) documents the API, architecture,
operations and decisions in Brazilian Portuguese. With Node.js 22 or newer,
run `npm --prefix docs-site ci` and `npm --prefix docs-site start`.
Use `npm --prefix docs-site run build` to generate the static site.

## Start PostgreSQL

1. From the project root in PowerShell, copy the development example once.
   Keep an existing `.env` and edit its values directly.

   ```powershell
   Copy-Item .env.example .env
   ```

   Set `POSTGRES_PORT=5434` to match the local Spring profile.

2. Set `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` and, if the
   default host port is occupied, `POSTGRES_PORT` in `.env`. Use local
   development values. Git ignores `.env`.
3. Validate, start and inspect the service.

   ```powershell
   docker compose config --quiet
   docker compose up -d postgres
   docker compose ps postgres
   ```

PostgreSQL listens on host loopback only. The named volume persists data
between restarts. The image uses the configured database and credentials
when initializing an empty volume. Changing `.env` later requires updating
the existing PostgreSQL database or role separately.

## Run Spring Boot from IntelliJ

1. Import the root Gradle project using the Wrapper and configure a JDK 25
   toolchain.
2. Create a Spring Boot or Application run configuration for
   `org.example.samplemicroservice.BoletoPaymentLabApplication` with JDK 25.
3. If `src/main/resources/application-local.yaml` is absent, copy
   `src/main/resources/application-local.example.yaml` to that path. Git
   ignores the local copy. Its JDBC URL uses host port 5434 and database
   `boleto_lab`. Adjust the URL locally if your Compose settings differ.
4. Set program argument `--spring.profiles.active=local` and provide the
   following variables if the local file uses placeholders. Use credentials
   that work in the existing PostgreSQL volume.

   | Variable | Example using the development defaults |
   | --- | --- |
   | `BPL_DB_USERNAME` | Value of `POSTGRES_USER` for a newly initialized volume |
   | `BPL_DB_PASSWORD` | Value of `POSTGRES_PASSWORD` |

5. Start the configuration after Compose reports PostgreSQL healthy.

Compose reads `.env` for interpolation. IntelliJ needs the Spring variables
in its run configuration. The local profile binds HTTP to loopback and
enables Flyway with `classpath:db/migration`. The API expects PostgreSQL.

## Start the local platform

Keep the existing PostgreSQL settings. In the ignored `.env`, set
`KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` before the first
Keycloak boot. Use a new local development credential. A Keycloak volume
retains its realm and initial administration settings.

```powershell
docker compose config --quiet
docker compose up -d
docker compose ps
```

The default loopback endpoints are PostgreSQL on the configured port,
Keycloak on 8180, Kafka on 9092, Kafbat UI on 8085, Jaeger UI on 16686
and OTLP/HTTP on 4318.
Kafka is a single-node KRaft development broker. Keycloak imports the
`boleto-lab` realm from `infra/keycloak`. PostgreSQL, Kafka and Keycloak
have separate named volumes. Jaeger stores traces in memory.
Open `http://127.0.0.1:8085` to inspect Kafka topics and consumer groups.
The UI uses `kafka:29092` on the Compose network; it does not expose Kafka
credentials or modify the existing topics.
To confirm the realm import, open
`http://127.0.0.1:8180/realms/boleto-lab/.well-known/openid-configuration`.
The first Keycloak startup can take several minutes on a cold Docker host.

Use `--spring.profiles.active=local,platform` in IntelliJ for Kafka and OTLP.
Override `BPL_OIDC_ISSUER`, `BPL_KAFKA_BOOTSTRAP` and `BPL_OTLP_ENDPOINT` if
their host ports differ from the defaults. For local-only API work, the
`local` profile keeps writing to the outbox and leaves delivery disabled.
Use `BPL_EVENTS_ENABLED` and `BPL_OTLP_ENABLED` to override either profile.

### Get an access token

Sign in to the Keycloak administration console at
`http://127.0.0.1:8180/admin/`. In the `boleto-lab` realm, open the
Credentials tab of each client used below and copy its generated secret into
Postman's local variable value. Use client credentials against
`http://127.0.0.1:8180/realms/boleto-lab/protocol/openid-connect/token`.
The resource server validates RS256 signatures, issuer, expiry and audience
`boleto-api`. It uses the issuer's JWKS endpoint with bounded timeouts.

| Client | Granted scopes |
| --- | --- |
| `boleto-issuer` | `boletos:issue`, `boletos:read` |
| `boleto-reader` | `boletos:read`, `boletos:list` |
| `boleto-operator` | `boletos:pay`, `boletos:cancel`, `boletos:audit` |
| `boleto-metrics` | `ops:metrics` |

Keep the client secrets and generated tokens in local Postman values. The
exported environment has empty values for these fields.

## Use the REST API

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/v1/boletos` | `boletos:issue`. Returns 201 with Location, 200 on replay, or 409 on key conflict. |
| GET | `/api/v1/boletos/{id}` | `boletos:read`. Retrieve the boleto as JSON. |
| GET | `/api/v1/boletos/{id}/pdf` | `boletos:read`. Render the educational PDF on demand. |
| GET | `/api/v1/boletos` | `boletos:list`. Filter and paginate. |
| POST | `/api/v1/boletos/{id}/payments` | `boletos:pay`. Simulate payment. |
| POST | `/api/v1/boletos/{id}/cancellations` | `boletos:cancel`. Cancel an issued boleto. |
| GET | `/api/v1/boletos/{id}/history` | `boletos:audit`. Inspect the ordered audit records. |

A temporary 409 includes `OPERATION_IN_PROGRESS` and `Retry-After: 1`.
A committed payload mismatch returns `IDEMPOTENCY_CONFLICT`. See the
[issuance use case](specs/usecases/UC-BPL-001-emitir-boleto.md) for examples.
The [lifecycle use case](specs/usecases/UC-BPL-002-boleto-lifecycle.md)
defines terminal states and filters. For example,
`?customerId=customer-demo-001&status=EMITIDO&page=0&size=20&sort=issuedAt,desc`.

## Import the Postman requests

1. In Postman, import both JSON files in [postman/](postman/) and select the
   **Boleto Payment Lab Local** environment.
2. Set the client secrets locally and run the matching **Get token** requests.
   Confirm `baseUrl` matches the local Spring port. Run the first issuance
   before its replays, lookup and PDF
   download. The request generates a fresh key and future due date and saves
   the UUID on success. Run the replays before starting another issuance.
3. Run the invalid amount, invalid due date and nonexistent lookup examples
   independently.
4. Run payment or cancellation on an issued boleto. Issue a fresh boleto to
   test the other terminal transition. The history request requires the
   operator client token.

## Observe local requests

`/actuator/health`, `/actuator/health/liveness` and
`/actuator/health/readiness` publish status only on loopback. Readiness
includes PostgreSQL. Access to `/actuator/metrics` and individual meters
requires `ops:metrics`. Administrative Actuator endpoints are disabled.

With the platform profile active, copy `X-Trace-Id` from a boleto response.
Open `http://127.0.0.1:16686/trace/<trace-id>` after export, or find
`BoletoPaymentLab` in the Jaeger search screen. Search the JSON application
logs for the same `traceId`; `spanId` identifies the particular operation.
`BPL_TRACE_SAMPLE` defaults to 1.0 for the local lab. Set a lower value
explicitly when preparing a shared deployment. The asynchronous OTLP
exporter has bounded timeouts and may drop telemetry during an outage.

## Delivery and migrations

V1 remains intact. V2 adds lifecycle columns and query indexes. Existing
records retain their UUID and due date, start in EMITIDO, and receive the
migration timestamp as their recorded issuance time. Expired rows become
VENCIDO on access or in bounded reconciliation. V3 adds audit and outbox
tables. V4 adds consumer receipts. V5 enforces the shared event ID, boleto ID
and aggregate version between audit and outbox. Existing issuance history is unavailable.
Spring Boot loads shared migrations from `db/migration` and selects the
database-specific directory under `db/vendor/{vendor}`. Remove any older
`spring.flyway.locations` override that only lists the shared directory, or
include both locations. H2 foundation startup uses the shared migrations.
There is no manual data migration for this release.

## Run the application image

The application identity is `BoletoPaymentLab`. Docker repositories require
lowercase names, so its matching local image is `boleto-payment-lab:local`.
The application service is opt-in and starts with the platform dependencies.

```powershell
docker compose --profile app build boleto-payment-lab
docker compose --profile app up -d boleto-payment-lab
docker compose --profile app ps boleto-payment-lab
```

The publisher claims at most one row per transaction with SKIP LOCKED,
sends with bounded timeouts and marks it published only after Kafka confirms.
Broker outages leave committed changes and events in PostgreSQL.
Retry uses exponential backoff with jitter capped at one minute.
This is at-least-once delivery. A crash between Kafka acknowledgement and
the database commit can replay a message. Consumers should deduplicate by
`eventId`; the educational consumer demonstrates this with a unique
PostgreSQL receipt. The broker and OIDC deployment are local development
services. Audit, outbox and receipt retention is intentionally manual for
this release.

## Run tests

Run `./gradlew test` (or `gradlew.bat test` on Windows). To include the
HTTP and PostgreSQL scenarios, set `BPL_IT_DB_URL`, `BPL_IT_DB_USERNAME`
and `BPL_IT_DB_PASSWORD` for a disposable or local development database
with schema creation permission. Each integration run uses a fresh
`bpl_it_*` schema, leaving it for inspection. Set `BPL_IT_KAFKA_BOOTSTRAP`
to exercise broker delivery as well. The integration suite uses a separate
topic and consumer group for each run. Set `BPL_IT_JAEGER_QUERY` to the Jaeger
base URL to verify trace export and lookup, and `BPL_IT_JAEGER_OTLP` if the
collector uses a non-default port. Without the integration URL,
the PostgreSQL tests are skipped and the unit and foundation tests still run.

## Scan for secrets

Install pre-commit, then enable the repository's hooks from the project root.
The Gitleaks hook installs the pinned version through pre-commit and scans the
Git index. Run it manually after preparing the staged changes.

```powershell
pre-commit install
pre-commit run gitleaks
```

With the standalone Gitleaks CLI (v8.30.1), run either of the following scans.
The staged scan also works before the first commit. For the history scan, use
the target branch of your pull request in place of `origin/main` if needed.

```powershell
gitleaks git --pre-commit --staged --redact=100 .
gitleaks git --redact=100 --log-opts="--full-history --diff-merges=first-parent origin/main..HEAD" .
```

The staged scan examines staged changes. Before staging, use `gitleaks dir`
against a candidate file or directory, for example
`gitleaks dir --redact=100 specs`. Directory scans can read ignored files,
so scope them to content intended for version control.

GitHub Actions scans PR commits with the MIT-licensed Gitleaks CLI container.
The workflow uses the default detection rules and redacts findings in logs.
A scan failure needs review before committing or merging.

## Security & Configuration

Copy `.env.example` to the ignored `.env` file and set values only for the
local environment. PostgreSQL variables are required by Compose. Before the
first Keycloak boot, set `KC_BOOTSTRAP_ADMIN_USERNAME` and
`KC_BOOTSTRAP_ADMIN_PASSWORD` to unique local values; Compose fails fast when
either is absent. Existing Keycloak volumes retain their initialized account,
so changing those variables later does not rotate an existing credential.

Keep `.env`, `application-local.yaml`, IDE workspace files, client secrets,
tokens and private keys outside Git. The application configuration accepts
database and platform settings through environment variables. Production
deployments must supply sensitive values through the platform secret manager
or an equivalent controlled mechanism, not through repository files.

Run `./gradlew test jacocoTestReport` before submitting a change. GitHub
Actions uploads coverage only when the repository is connected to Codecov and
the `CODECOV_TOKEN` GitHub Actions secret is configured. Fork pull requests
never receive that secret. Rotate a credential immediately if it is exposed
outside the approved secret store, then update the affected local or platform
configuration.
