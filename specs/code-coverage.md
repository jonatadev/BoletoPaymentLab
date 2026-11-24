# Code coverage integration

Status: Approved for implementation under RES-BPL-004.

JaCoCo produces XML and HTML coverage reports from the Gradle `test` and
`integrationTest` tasks. PostgreSQL and Kafka scenarios run through disposable
Testcontainers instances. Each named PostgreSQL fixture database has its own
business schema and Flyway-managed `analytics` objects. The JDBC URL comes
from the fixture container, so the suite leaves the development database's
`public`, `analytics`, and any legacy `bpl_it_*` schemas intact. The optional
Jaeger lookup remains explicitly
guarded by its documented environment because it verifies a separately managed
collector.

`test` contains fast domain and collaborator tests. `integrationTest` contains
Spring, Flyway, JPA, HTTP, PostgreSQL, Outbox, Kafka and migration coverage.
The Testcontainers lifecycle is per integration-test JVM, without persistent
reuse across local or CI executions. Awaitility expresses bounded observable
conditions for asynchronous Kafka delivery instead of fixed sleeps. WireMock is
not included because no production HTTP client to an external business service
exists in the implementation.

GitHub issue #7 covers integration database isolation. The `Test coverage`
workflow runs the PostgreSQL and MCP suites on pull requests targeting
`main`. Verify the fixture boundary with the integration suite and a
separate PostgreSQL instance configured as a legacy development datasource.
The suite should leave that instance's schemas and fixture rows intact.

Codecov receives the XML report only from trusted workflow runs with an
explicit repository token. Fork pull requests still execute tests and generate
the report, but cannot receive a repository secret. Project and patch statuses
are initially informational to establish a measured baseline without blocking
legacy code.

Trace: RES-BPL-001, UC-BPL-001 S01-S13, UC-BPL-002 S14-S21.
