# Boleto Payment Lab REST OpenAPI documentation

Status: Draft for human review.

## Scope and traceability

Document RF-BPL-001 through RF-BPL-009 and RNF-BPL-003/004 in
[the requirements catalog](requirements_catalog.md). Trace issuance, replay,
validation and PDF to [UC-BPL-001](usecases/UC-BPL-001-emitir-boleto.md)
S01–S13. Trace lifecycle, filters, audit and authorization to
[UC-BPL-002](usecases/UC-BPL-002-boleto-lifecycle.md) S14–S17 and S20–S21.
Technical revisions follow [the revision specification](boleto-revisions.md).
Keep the REST implementation, migrations and MCP protocol unchanged.

## Proposed contract

| Operations | Required scope |
| --- | --- |
| POST /api/v1/boletos | boletos:issue |
| GET /api/v1/boletos | boletos:list |
| GET /api/v1/boletos/{id} and /{id}/pdf beneath the same collection | boletos:read |
| POST /api/v1/boletos/{id}/payments | boletos:pay |
| POST /api/v1/boletos/{id}/cancellations | boletos:cancel |
| GET /api/v1/boletos/{id}/history, /revisions, /revisions/{revision}, /revisions/compare | boletos:audit |
| GET /actuator/health, /actuator/health/liveness, /actuator/health/readiness | Anonymous |
| GET /actuator/metrics and /actuator/metrics/{name} | ops:metrics |

Generate the boleto paths and DTO schemas from Spring MVC using
springdoc-openapi 3.2.1, whose release targets Boot 4.2.0. Verify dependency
resolution and application startup on Java 25 before accepting the pin.
Explicitly describe the allowlisted operations endpoints if they cannot
be inferred accurately from Actuator. Exclude MCP and administrative routes.

Use OpenAPI 3.0.4. Add stable operation IDs and consumer-facing descriptions
and constrain the issuance request explicitly because the controller
accepts a JSON tree. Require exactly the three input fields and a single
`Idempotency-Key`. Capture canonical decimal validation and due-date rules.
Document issuance 201 with Location, replay 200, and both 409 variants.
Describe the PDF as synthetic, with a binary body and Content-Disposition.
Document actual problem codes, including 503 for recognized database outages.
Security 401/403 responses omit the business error detail field. Do not
promise a 422 response.

Represent the Keycloak client credentials flow and operation scopes in
OpenAPI. Swagger UI accepts an externally acquired Bearer JWT. Keep the
service client secrets outside browser configuration and disable persistent
authorization. Enable the UI and API docs only via explicit local or test
configuration. API authorization remains unchanged.

## Artifact and verification

Generate `docs-site/static/openapi/boleto-payment-lab-v1.yaml` from a
deterministic test context with synthetic configuration. Provide a Gradle
regeneration command and have CI compare generated and published contracts
after canonicalization. Use an OpenAPI parser for validation and focused
contract assertions. Evaluate Spectral only if it adds distinct checks.

| ID | Acceptance scenario |
| --- | --- |
| OAS-S01 | Boot 4.2 starts with the pinned springdoc, and enabled local endpoints serve OpenAPI and Swagger UI. |
| OAS-S02 | All ten MVC operations and the selected operations endpoints have stable unique IDs, valid references and correct scopes. |
| OAS-S03 | The contract represents issuance 201/200/409, bounded request schemas, real problem responses and binary PDF. |
| OAS-S04 | Default configuration keeps documentation endpoints unavailable. REST authorization and existing tests pass. |
| OAS-S05 | CI rejects contract drift. The Portuguese Docusaurus reference links to the generated contract and passes typecheck/build. |

PENDENTE: hosted CI and PR verification require publication authorization.
Use the existing Docusaurus REST reference for the Portuguese integration
guide and link the downloadable YAML using the site's base URL.
