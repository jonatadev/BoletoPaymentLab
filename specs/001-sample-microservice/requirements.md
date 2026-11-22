# Requirements

## Functional requirements

| ID | Requirement | Source |
|---|---|---|
| RF01 | The application must start as a Spring Boot application. | Existing specification |
| RF02 | The Spring context must be published through `SampleMicroserviceApplication`. | Existing specification |
| RF03 | The application must support local execution with H2. | Existing specification |
| RF04 | PostgreSQL must be configurable through external properties. | Existing specification |
| RF05 | Database migrations must be managed by Flyway. | Existing specification |
| RF06 | Persistence entities and repositories must be supported by Spring Data JPA. | Existing specification |
| RF07 | Automated tests must validate application startup. | Existing specification |

## Non-functional requirements

| ID | Requirement | Source |
|---|---|---|
| RNF01 | Use Java 25 according to the Gradle toolchain. | Existing specification |
| RNF02 | Use the Gradle Wrapper for builds. | Existing specification |
| RNF03 | Do not version credentials or environment-specific configuration. | Existing specification |
| RNF04 | Separate development-only dependencies from runtime dependencies. | Existing specification |
| RNF05 | Use versioned database migrations instead of ad hoc schema changes. | Existing specification |

## Constraints

- Spring Boot 4.2.0.
- Java 25.
- Gradle Kotlin DSL.
- Spring Web MVC.
- Spring Data JPA.
- Flyway.
- H2 for local development and tests.
- PostgreSQL for environments requiring external persistence.

## Acceptance criteria

| ID | Criterion |
|---|---|
| CA01 | `./gradlew test` succeeds with Java 25 configured. |
| CA02 | `SampleMicroserviceApplicationTests.contextLoads()` loads the Spring application context. |
| CA03 | The application starts on H2 without an external PostgreSQL service. |
| CA04 | Database configuration can be supplied externally without Java changes. |
| CA05 | Database credentials and secrets are not exposed by default. |

## Assumptions

- The project is a foundation without domain APIs or domain use cases.
- H2 remains suitable for local development and tests unless the specification changes.
- Production-specific configuration will come from the deployment environment.

## Out of scope

- Domain REST APIs.
- Domain data modeling.
- Authentication and authorization implementation.
- Advanced observability.
- Container deployment and orchestration.
- Environment, release, and disaster-recovery strategy.
