---
name: spring-microservice
description: Development practices for Spring Boot microservices, REST APIs, persistence, testing, configuration, observability, security, and Gradle Kotlin DSL in this project.
---

# Spring Microservice Development

Use this skill when creating or modifying Java code in this project.

## Language requirement

- Write all code, comments, identifiers, tests, documentation, specifications, and generated responses in English.
- Reject or rewrite content in any other language.

## Project conventions

- Target Java 25 and Spring Boot 4.2.
- Use Gradle Kotlin DSL in `build.gradle.kts`.
- Keep production code under `src/main/java/org/example/samplemicroservice`.
- Keep configuration under `src/main/resources/application.yaml`.
- Keep tests under `src/test/java`.
- Prefer constructor injection. Do not use field injection or Lombok to hide dependencies.
- Put application startup and configuration in the existing `org.example.samplemicroservice` package.

## Package and class structure

Create feature packages instead of generic `controller`, `service`, or `repository` packages:

```text
org.example.samplemicroservice.<feature>
org.example.samplemicroservice.<feature>.api
org.example.samplemicroservice.<feature>.domain
org.example.samplemicroservice.<feature>.infra
```

- Define controllers only for HTTP concerns.
- Define application services for use-case orchestration.
- Define domain types and business rules outside framework-specific code.
- Use Spring Data repositories for persistence, not for business logic.
- Do not expose JPA entities directly through REST API responses.
- Keep classes small and use interfaces only when multiple implementations or test seams are useful.

## REST API

- Use resource-oriented routes and standard HTTP methods.
- Return appropriate status codes, including `201`, `204`, `400`, `404`, `409`, and `422`.
- Define explicit request and response DTOs.
- Validate external input with Bean Validation annotations and return structured validation errors.
- Never trust client-supplied identifiers or authorization data.
- Use `@Valid` on request bodies and method parameters that require validation.
- Add pagination for collection endpoints and use stable sorting.
- Do not return stack traces or internal database details to clients.
- Use API versioning only when required by a documented compatibility policy.

## Persistence

- Use Flyway migrations for every database schema change.
- Put migrations under `src/main/resources/db/migration`.
- Use immutable, ordered migration names such as `V1__create_customer.sql`.
- Prefer PostgreSQL-compatible SQL and keep H2 usage limited to local or test scenarios.
- Use transactions only around operations that require atomicity.
- Keep transaction boundaries in application services, not controllers or repositories.
- Fetch only the data the use case needs; avoid lazy-loading surprises and `N+1` queries.
- Do not use database-generated identifiers as stable public API identifiers.

## Configuration

- Configure external environment values through environment variables or an externalized configuration source.
- Never commit secrets, credentials, passwords, or private endpoints.
- Use typed configuration properties with `@ConfigurationProperties` instead of spreading `@Value` annotations.
- Keep safe defaults in `application.yaml`.
- Use Spring profiles only for environment-specific differences, not for business behavior.
- Redact secrets and sensitive headers in logs.

## Resilience and observability

- Set timeouts for HTTP clients and database operations.
- Implement retries only for retryable failures and always use bounded backoff.
- Do not retry requests with ambiguous or non-idempotent side effects.
- Use health checks, structured logs, metrics, and distributed tracing.
- Log correlation IDs and avoid logging full payloads or personal data.
- Use consistent operational error logs with an error code and contextual identifiers.
- Prefer Spring's supported observability integrations over custom instrumentation.

## Security

- Require authentication and authorization at every service boundary.
- Use deny-by-default authorization rules.
- Validate, encode, and escape all external input and output as appropriate.
- Use parameterized queries and Spring Data query methods or JPQL; never concatenate SQL.
- Keep CORS restricted to known origins.
- Keep security configuration out of feature code and centralize it.
- Add security tests for authorization, authentication, and data ownership.

## Error handling

- Use one `ProblemDetail`-based error response shape for the API.
- Map domain exceptions to stable HTTP responses.
- Include a public error code and a human-readable message.
- Avoid exposing internal exception messages that can reveal implementation details.
- Log unexpected failures once at the service boundary.

## Testing

- Use JUnit 5 and AssertJ-style assertions.
- Add unit tests for domain and application logic.
- Add slice tests for controllers, repositories, and persistence behavior.
- Add `@SpringBootTest` context tests only where full application wiring is required.
- Prefer testcontainers or a PostgreSQL-compatible test setup when behavior depends on a real database.
- Use H2 only for isolated tests whose SQL remains portable.
- Test success and failure cases, validation, authorization, and transaction boundaries.
- Name tests by behavior, for example `shouldRejectDuplicateEmail`.
- Do not add a test that only verifies framework or Lombok-generated behavior.

## Build and dependencies

- Keep dependencies explicit in `build.gradle.kts`.
- Use dependency management BOMs instead of hard-coding Spring dependency versions.
- Add only dependencies needed by the microservice.
- Use Gradle tasks for formatting and verification.
- Run `./gradlew test` for the smallest relevant verification.
- Run `./gradlew build` when the complete project needs validation.
- Keep generated reports and build output out of source control.

## Implementation checklist

Before completing a change:

1. Confirm the use case and public behavior.
2. Design DTO, domain, persistence, and error boundaries.
3. Add Flyway migrations before relying on schema changes.
4. Implement constructor-injected components.
5. Validate external input and authorization.
6. Add focused unit, slice, or integration tests.
7. Run the smallest relevant Gradle test task.
8. Check for secrets, sensitive logs, and accidental entity exposure.
