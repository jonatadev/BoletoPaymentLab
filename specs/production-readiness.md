# Production readiness review

Status: PENDENTE - review before any production deployment.

The current Compose topology is an educational local environment. It binds host
ports to loopback and runs the application as a non-root user, but Kafka uses
PLAINTEXT and Keycloak uses `start-dev`. A production deployment must provide
TLS termination, authenticated and encrypted messaging, approved image
provenance, secret-manager delivery, and a PostgreSQL application role with
least privilege.

Authorization currently grants operation scopes across the synthetic lab data.
Tenant and resource ownership enforcement are not implemented. Decide the
authenticated customer-to-boleto mapping before processing non-synthetic or
multi-tenant data.

Trace: RES-BPL-002, RES-BPL-003, RNF-BPL-003, RNF-BPL-004, UC-BPL-002 S20-S21.
