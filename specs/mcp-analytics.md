# PostgreSQL MCP analytics server

Status: Draft for human review and approval before implementation.

## Scope and traceability

This specification introduces a separately deployable, read-only MCP server for
synthetic boleto analytics. It extends the lab constraints in RES-BPL-001,
RES-BPL-002, RES-BPL-003 and RES-BPL-004. It reads the aggregates created by
RF-BPL-001, RF-BPL-007, RF-BPL-009 and RF-BPL-010 and must not change their
transactions, REST API, Kafka delivery, audit trail or Flyway history.

The relevant acceptance coverage is UC-BPL-001 S01-S13 and UC-BPL-002 S14-S21.
The MCP server adds the following proposed acceptance scenarios.

| ID | Scenario |
| --- | --- |
| MCP-S01 | A client discovers exactly the six read-only analytics tools. |
| MCP-S02 | Summary, daily and status tools return aggregates for a bounded date range. |
| MCP-S03 | Invalid, inverted and excessive date ranges are rejected before database access. |
| MCP-S04 | Outbox and processing tools return only metrics represented by the current schema. |
| MCP-S05 | The PostgreSQL reader role can select approved analytics views and cannot write or run DDL. |
| MCP-S06 | Anonymous and insufficient-scope HTTP MCP calls are rejected without tool execution. |
| MCP-S07 | The HTTP transport exposes no arbitrary SQL, personal customer identifiers, audit correlation IDs or event payloads. |

## Existing model evidence

The PostgreSQL model has the following confirmed facts.

| Source | Confirmed fields used by analytics |
| --- | --- |
| `boleto` | `amount NUMERIC(8,2)`, `due_date DATE`, `status`, `issued_at TIMESTAMPTZ` and UUID identity. |
| `boleto_outbox` | `occurred_at`, `next_attempt_at`, `attempts` and nullable `published_at`. |
| `processed_boleto_event` | one receipt per event with `processed_at`. |

The allowed boleto statuses are `EMITIDO`, `PAGO`, `VENCIDO` and `CANCELADO`.
No table records a processing failure state or a processing start timestamp.
Consequently, the processing tool will expose processed receipts and outbox
pending/published counts. It will explicitly identify failed-operation and
average-duration metrics as unavailable instead of inferring them.

## Architecture decision

Use a new Gradle subproject named `boleto-analytics-mcp`.

The subproject is a standalone Spring Boot 4.2 application using Java 25,
Spring AI 2.1.0 and `spring-ai-starter-mcp-server-webmvc`. It uses the
Streamable HTTP MCP transport at `/mcp`, Spring MVC, JDBC and Spring Security.
It does not depend on the main application module, JPA, Flyway, Kafka or
application write models.

This choice keeps the operational API process unchanged and limits the
analytics process to SQL projections over approved views. It avoids mixing an
MCP protocol endpoint with the boleto API security chain.

STDIO is not part of the initial deliverable. Streamable HTTP supports a
separate process and authenticated remote clients. A future STDIO launcher can
reuse the analytics service after client requirements are verified.

## PostgreSQL access model

A new immutable Flyway migration, proposed as `V7__analytics_views.sql`, will
create an `analytics` schema and four aggregate-only views.

| View | Purpose | Excluded data |
| --- | --- | --- |
| `analytics.boleto_daily` | issued date, boleto count and amount total | UUID, customer ID, due date |
| `analytics.boleto_status` | issued date, status, count and amount total | UUID, customer ID, due date |
| `analytics.outbox_summary` | occurrence time, publication state and retry-attempt indicator | event ID, aggregate ID, trace context, correlation ID |
| `analytics.processing_summary` | processed receipt count by processing date | event ID and aggregate ID |

`analytics.boleto_summary` is calculated from `analytics.boleto_status` by
parameterized SQL, preserving a single source for status aggregates. The views
will not expose `customer_id`, UUIDs, request keys, event IDs, trace context,
audit actors or revision data.

An administrator-run provisioning script outside Flyway will create the
`boleto_mcp_reader` login, grant `CONNECT` on the configured database, grant
`USAGE` on `analytics`, and grant `SELECT` only on the approved views. It will
revoke public access from the analytics schema and views. The script is not
executed by the main application or by the MCP server.

PENDENTE: confirm whether the database owner that deploys V7 is the approved
owner of security-definer views. The initial design prefers normal invoker
views owned by the migration owner and grants only the views to the reader.

## Tool contracts

All tools return structured JSON records. Date inputs use ISO-8601 calendar
dates and are validated as an inclusive range no longer than 366 days.

| Tool | Input | Output |
| --- | --- | --- |
| `get_boleto_summary` | optional `startDate`, `endDate` | total count, total amount, average amount, status counts and effective period. |
| `get_boletos_by_day` | required `startDate`, `endDate` | chronological day, count and amount total rows. |
| `get_boletos_by_status` | optional `startDate`, `endDate` | status, count, percentage and amount total rows. |
| `get_processing_metrics` | no input | processed receipt count, pending/published outbox counts and declared unavailable metrics. |
| `get_outbox_metrics` | no input | total, pending, published, retry-attempt count and oldest pending age. |
| `get_database_health` | no input | availability, bounded `SELECT 1` latency and PostgreSQL major version. |

PENDENTE: confirm the default interval for optional tool dates. The proposed
default is the 30 calendar days ending today in the configured application
timezone. Explicit dates remain mandatory for `get_boletos_by_day`.

No tool accepts SQL text, table names, columns, sorting expressions or
unbounded record limits. Every repository query uses fixed SQL and named
parameters.

## Security and operational configuration

The MCP application requires a JWT with audience `MCP_AUTH_AUDIENCE` and scope
`analytics:read` for `/mcp`. Health probes are limited to loopback or the
internal Docker network. Spring Security applies deny-by-default authorization,
stateless sessions and controlled CORS. The initial Compose binding is
`127.0.0.1:${MCP_SERVER_PORT}:8090`.

The datasource uses `MCP_DATABASE_URL`, `MCP_DATABASE_USERNAME` and
`MCP_DATABASE_PASSWORD`, a maximum pool size of four, connection timeout of
three seconds, statement timeout of five seconds and read-only transactions.
Flyway and SQL initialization are disabled in the MCP process.

The service records counters and timers by one of six fixed tool names. It
records authentication and query failures without JWT values, SQL parameters,
customer data or correlation IDs.

PENDENTE: set the authorized production ingress and rate-limit policy. The
initial Compose profile keeps the service loopback-bound. A production gateway
must provide TLS and a rate limit before any non-local exposure.

## Docker and local operation

Compose will add an optional `mcp` profile. The service joins the internal
network, depends on a healthy PostgreSQL service, runs as a non-root user and
does not publish the database. It receives only MCP-specific reader
credentials from an ignored `.env` file.

The main application remains optional and does not depend on the MCP profile.
The MCP container image builds only the analytics subproject.

## Verification plan

| Layer | Coverage |
| --- | --- |
| Unit | date range validation, amounts, empty aggregates and unavailable metrics. |
| PostgreSQL Testcontainers | V7 views, fixed aggregate queries, reader grants and rejected writes. |
| MCP HTTP contract | tool discovery, structured schemas, valid calls, invalid arguments, unauthenticated calls, insufficient scopes and absence of write tools. |
| Build and documentation | Gradle `clean build`, JaCoCo XML and HTML, Docusaurus typecheck and build. |

The existing GitHub Actions coverage workflow will execute the new unit and
PostgreSQL integration tests. MCP tests will not start Kafka or Keycloak;
synthetic signed JWT support will be local to the test process.

## Documentation deliverables

The Brazilian Portuguese portal will add an "AI Integration — PostgreSQL MCP
Server" page. It will document architecture, Streamable HTTP, tool contracts,
reader-role provisioning, Docker profile, local commands, test coverage,
limitations and verified client configurations. Codex, Claude Code, Docker MCP
Toolkit and ChatGPT examples will be included only when their documented
connection mechanism is verified against the resulting server.
