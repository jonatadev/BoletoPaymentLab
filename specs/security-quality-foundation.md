# Security and Quality Foundation

Status: Draft for human review.

## Traceability

- RES-BPL-003: preserve the modular monolith and its existing delivery flow.
- RES-BPL-004: record scope and acceptance criteria before implementation.
- RNF-BPL-003: protect the delivery path without weakening application security.
- RNF-BPL-004: keep verification observable and separate from application behavior.

## Current state

The repository has a Java 25 Gradle root build, the independent
`boleto-analytics-mcp` Gradle module, root and MCP Dockerfiles, Compose image
references, coverage CI, documentation CI and a Gitleaks pull-request workflow.
The existing scanner uses a pinned container image and scans the PR commit
range, but does not run on `main` pushes or manual dispatches.

## Approved-scope proposal

### S-QF-01 Dependabot

Add `.github/dependabot.yml` with weekly `America/Sao_Paulo` updates for the
root Gradle build, GitHub Actions and both project Dockerfiles. Minor and patch
updates are grouped per ecosystem; major updates remain separate. Open PRs are
bounded and carry `dependencies` plus an ecosystem-specific label. No automatic
merge is configured.

### S-QF-02 CodeQL

Add a distinct `CodeQL` workflow for Java/Kotlin on pushes and PRs to `main`, a
weekly schedule and manual dispatch. It uses CodeQL `security-extended` and a
manual Gradle build suitable for both Gradle modules. Permissions are
`contents: read` and `security-events: write`; no secrets or
`pull_request_target` are used.

### S-QF-03 Gitleaks

Rename/evolve the existing `gitleaks.yaml` rather than duplicate it. It scans
PR commit ranges, full history for `main` pushes, and full history on manual
dispatch. It retains the pinned image digest, redacts findings and uses
least-privilege permissions. No custom allowlist is proposed.

### S-QF-04 Security policy

Add an English `SECURITY.md` with supported-branch guidance, GitHub Private
Vulnerability Reporting, required report evidence, disclosure process and the
educational/non-production scope.

### Documentation

Add a concise Brazilian Portuguese Security & Quality section to the existing
operations security page. It covers local scanner commands, CI failure meaning,
and GitHub settings that maintainers must enable manually.

## Acceptance scenarios

1. Dependabot recognizes valid Gradle, GitHub Actions and Docker update
   directories without auto-merge configuration.
2. CodeQL analyses Java/Kotlin source for both Gradle modules and publishes
   findings to GitHub Code Scanning when that feature is enabled.
3. A synthetic secret is detected locally without committing it.
4. Gitleaks scans commit history/ranges rather than only a working tree.
5. Security reports can be submitted privately using the documented advisory
   URL.
6. Existing coverage and documentation workflows are unchanged except where
   explicitly required by this scope.

## Decisions and limits

- PENDENTE: repository maintainers must enable Dependabot alerts/security
  updates, code scanning, private vulnerability reporting and branch/ruleset
  checks in GitHub settings, subject to repository visibility and plan.
- Excluded: OWASP Dependency-Check, SBOM generation, CycloneDX,
  Dependency-Track, auto-merge and deployment changes.
