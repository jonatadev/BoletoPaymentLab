---
name: automated-testing
description: Test planning and implementation conventions for JUnit 5, AssertJ, Spring slices, integration tests, and Gradle verification in this microservice.
---

# Automated Testing

Use this skill for test planning, test implementation, test review, and test failure diagnosis.

## Language requirement

- Write test names, assertions, comments, fixtures, documentation, and reports in English.
- Reject or rewrite content in any other language.

## Test levels

- Unit test domain rules and application use cases with minimal framework setup.
- Use `@WebMvcTest` or a compatible Spring MVC slice for controller input, status, and serialization behavior.
- Use a JPA or repository slice for persistence behavior only when it differs from straightforward framework behavior.
- Use `@SpringBootTest` for full context wiring or cross-component integration.
- Prefer Testcontainers or PostgreSQL-compatible setup for database-specific behavior.
- Keep H2 for isolated tests with portable SQL.

## Test conventions

- Use JUnit 5 and AssertJ-style assertions.
- Name tests after behavior, for example `shouldRejectDuplicateEmail`.
- Use arrange-act-assert structure.
- Test success and relevant failure paths.
- Cover Bean Validation, stable HTTP status and error shape, authorization, and data ownership.
- Test transaction boundaries when a use case has atomic or rollback requirements.
- Avoid timing-based assertions unless they are the behavior under test.
- Keep fixtures focused and reusable; do not add broad setup unrelated to the scenario.

## Spring Boot behavior

- Verify externally observable responses and state changes.
- Do not test framework-generated getters, setters, or constructors.
- Do not assert private implementation details.
- Avoid full application context startup for a controller or repository unit.
- Use separate test slices when slice dependencies are clearer and faster.

## Gradle verification

- Run `./gradlew test` for the full test suite.
- Run `./gradlew test --tests '*ClassName*'` for a focused test class.
- Run `./gradlew test --tests '*methodName*'` when Gradle supports the exact selector.
- Run `./gradlew build` only when complete build validation is required.
- Capture the failing test, assertion, and relevant stack section; do not treat framework context startup logs as the root cause without evidence.

## Security test coverage

- Include anonymous, authenticated, insufficient-authority, and cross-owner cases.
- Verify denied operations do not mutate data.
- Verify security errors use the public `ProblemDetail` response convention.
- Verify that denied resources do not reveal existence or internal identifiers.
