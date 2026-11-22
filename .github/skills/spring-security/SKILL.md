---
name: spring-security
description: Implement and review Spring Security configuration, deny-by-default authorization, data ownership, CORS, CSRF, and security tests for this Spring Boot microservice.
---

# Spring Security

Use this skill for authentication or authorization implementation and security review.

## Language requirement

- Write security code, comments, identifiers, tests, findings, documentation, and generated responses in English.
- Reject or rewrite content in any other language.

## Architecture

- Centralize security configuration in a dedicated configuration class.
- Keep feature code independent of authentication filters and low-level security state.
- Require authentication and authorization at every service boundary.
- Use deny-by-default rules for HTTP endpoints and keep public endpoints explicitly enumerated.
- Use method security when it adds protection beyond route-level authorization.
- Keep authorization decisions close to the application use case or an explicit policy component.

## HTTP security

- Configure a `SecurityFilterChain` using the Spring version and APIs present in this project.
- Disable CSRF only for a documented non-browser stateless API design.
- For browser-facing endpoints, enable CSRF protection and configure cookie or bearer mechanisms consistently.
- Restrict CORS to known origins and required methods and headers.
- Avoid wildcard origins in production.
- Set secure session, cookie, and cache headers appropriate to the deployment model.
- Treat forwarded headers and proxy configuration as deployment-sensitive.

## Authorization

- Grant the minimum required role or authority.
- Check object ownership in addition to roles when resources belong to users or tenants.
- Never trust client-supplied user, owner, tenant, role, or authorization identifiers.
- Map domain authorization failures to a stable `ProblemDetail` response without exposing internal policy details.
- Do not log tokens, credentials, authorization headers, or personal data.

## Input and output

- Validate external input at the API boundary and encode or escape output as required by the response type.
- Use parameterized Spring Data queries or JPQL; never concatenate SQL.
- Prevent sensitive fields and internal identifiers from being exposed in DTOs.
- Keep internal exception messages out of client responses.

## Testing

- Test anonymous access, authenticated access, missing permissions, insufficient roles, and denied ownership.
- Test valid and invalid credentials or tokens only at integration boundaries when authentication is implemented.
- Use Spring Security test support only for scoped, behavior-driven slice or integration tests.
- Add a context test only when full security wiring must be proven.

## Completion check

1. Every endpoint has an intentional public or protected decision.
2. CSRF and CORS match the actual client model.
3. Authorization checks include ownership or tenant isolation where applicable.
4. Security failures use consistent public error responses.
5. Focused security tests pass.
6. Secrets and sensitive headers are absent from logs and configuration files.
