# Code coverage integration

Status: Approved for implementation under RES-BPL-004.

JaCoCo produces XML and HTML coverage reports from the existing Gradle test
task. The configured PostgreSQL, Kafka and Jaeger scenarios contribute when
their documented `BPL_IT_*` environment is available; they are not replaced
with synthetic coverage.

Codecov receives the XML report only from trusted workflow runs with an
explicit repository token. Fork pull requests still execute tests and generate
the report, but cannot receive a repository secret. Project and patch statuses
are initially informational to establish a measured baseline without blocking
legacy code.

Trace: RES-BPL-001, UC-BPL-001 S01-S13, UC-BPL-002 S14-S21.
