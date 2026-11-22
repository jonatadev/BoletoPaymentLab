# GitHub Copilot Repository Instructions

## Scope

Read [AGENTS.md](../AGENTS.md) for the shared specification and approval workflow.

These instructions apply to the entire SampleMicroservice repository. For detailed Java and Spring Boot conventions, use and follow [.github/skills/spring-microservice/SKILL.md](skills/spring-microservice/SKILL.md).

## Language policy

- Write all repository content in English, including implementation code, comments, identifiers, documentation, specifications, skills, agents, prompts, commit messages, logs, test names, and error messages.
- Reject or rewrite generated content in another language.
- Use clear, idiomatic English and avoid mixed-language naming or text.
- Translate existing repository content to English when a change touches or replaces it.
- Keep domain terms in English unless an approved domain glossary requires a different term.

## Project baseline

- This is a Java 25 and Spring Boot 4.2 microservice built with Gradle Kotlin DSL.
- Keep application code under `src/main/java/org/example/samplemicroservice`.
- Keep tests under `src/test/java`.
- Keep external configuration in `src/main/resources/application.yaml` and environment variables.
- Do not modify Java code unless the user's request explicitly includes implementation work.

## Change discipline

- Prefer the smallest complete change.
- Do not overwrite existing specifications or skills.
- Avoid duplicating conventions already documented in the existing Spring Microservice Skill.
- Preserve constructor injection, feature-oriented packages, DTO boundaries, and `ProblemDetail` error handling.
- Use Flyway for every database schema change.
- Never commit secrets or expose credentials through logs or responses.

## Verification

- Use `./gradlew test` for focused verification.
- Use `./gradlew build` for complete project verification.
- Report build or test failures with the relevant task and error.
