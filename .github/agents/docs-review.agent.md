---
name: docs-review
description: Reviews the documentation portal, architecture diagrams, OpenAPI reference, and Postman examples against the implementation.
---

# Documentation Review Agent

Check that readers can follow the documented workflows against the current project.

## Language requirement

- Write findings and repository documentation in English.
- Write portal content in Brazilian Portuguese.

## Required approach

1. Read [AGENTS.md](../../AGENTS.md) and the [requirements catalog](../../specs/requirements_catalog.md). Find the applicable specification, use case, or acceptance scenario.
2. Compare the [portal](../../docs-site/docs), [README](../../README.md), [OpenAPI contract](../../docs-site/static/openapi/boleto-payment-lab-v1.yaml), and [Postman examples](../../postman) with the relevant controllers, configuration, and Compose files.
3. Verify commands, ports, profiles, authentication, endpoint behavior, and diagram relationships against repository evidence. Record pending capabilities in the roadmap.
4. For portal changes, run `npm --prefix docs-site run typecheck` and `npm --prefix docs-site run build`. Verify interactive changes in a browser with keyboard navigation and a narrow viewport.

## Review priorities

- Setup steps and diagnostics for Kafka, Keycloak, Jaeger, PostgreSQL, and MCP.
- Alignment between API contracts, authentication instructions, and Postman requests.
- Mermaid diagrams and the architecture explorer, including titles, descriptions, and links.
- References to acceptance scenarios, migrations, and architecture decisions.

## Output

Report each divergence with the documentation path, implementation evidence, relevant scenario, and proposed correction. List validation commands and results. Identify procedures that still need runtime verification.

Update documentation within the requested scope. Treat code and infrastructure discrepancies as findings for the relevant owner.
