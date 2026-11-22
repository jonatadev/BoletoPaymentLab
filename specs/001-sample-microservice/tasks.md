# Tasks

## Foundation

- [ ] T001 Verify that Java 25 is available and the Gradle Wrapper can execute.
- [ ] T002 Verify that the Spring application context loads with the current H2-compatible setup.
- [ ] T003 Confirm that external database properties override the safe local defaults without Java changes.
- [ ] T004 Confirm that `application.yaml` contains no credentials or environment-specific secrets.

## Persistence

- [ ] T005 Create versioned Flyway migrations for the first schema, before relying on a domain persistence model.
- [ ] T006 Keep persistence entities and repositories in feature-oriented infrastructure packages.
- [ ] T007 Keep entities out of public REST DTOs.
- [ ] T008 Select H2 or PostgreSQL test setup according to the SQL behavior under test.

## API foundation

- [ ] T009 Add explicit request and response DTOs before introducing REST endpoints.
- [ ] T010 Add Bean Validation and structured `ProblemDetail` responses for external input.
- [ ] T011 Map domain failures to stable HTTP statuses and public error codes.
- [ ] T012 Use stable resource routes and appropriate status codes.

## Testing

- [ ] T013 Preserve a focused application context test.
- [ ] T014 Add behavior-named unit tests for domain and application logic.
- [ ] T015 Add controller slice tests for request validation and response behavior.
- [ ] T016 Add persistence slice tests only for database behavior that needs framework support.
- [ ] T017 Run `./gradlew test --tests '*SampleMicroserviceApplicationTests*'` after startup changes.
- [ ] T018 Run `./gradlew test` before finalizing repository changes.
- [ ] T019 Run `./gradlew build` when complete build verification is required.

## Future security tasks

- [ ] T020 Define public and protected endpoint boundaries.
- [ ] T021 Configure authentication and deny-by-default authorization.
- [ ] T022 Define CORS and CSRF behavior based on the client model.
- [ ] T023 Define object ownership or tenant isolation policies.
- [ ] T024 Add authentication, authorization, and ownership tests.
- [ ] T025 Run the security review agent before approving security implementation.
