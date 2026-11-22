# Shared repository instructions

These instructions apply to Codex and GitHub Copilot. Read the applicable
Boleto Payment Lab specification before proposing or changing behavior.

1. **Specify.** Link the relevant catalog IDs, model concepts, use case and
   acceptance scenarios. Record open business decisions as PENDENTE.
2. **Review.** Obtain human approval of the relevant specification revision
   before implementing it. Surface gaps and conflicts for review.
3. **Implement.** Work within the approved scope and preserve the historical
   foundation under `specs/001-sample-microservice/`.
4. **Test.** Derive tests from approved scenarios. Report their IDs, commands,
   results and any gaps.

Use English for repository content outside the Brazilian Portuguese portal.
Use the requested language for conversation.
Keep credentials and personal data out of fixtures, logs and version control.

## Technical documentation

- Update the relevant pages in `docs-site/docs/` whenever contracts,
  architecture or behavior change. Link requirements and scenarios in
  `specs/` and keep setup commands and Postman examples aligned.
- Write portal content in Brazilian Portuguese. Describe current behavior
  in the reference pages and keep unimplemented proposals in the roadmap.
- Verify changes with `npm --prefix docs-site run typecheck` and
  `npm --prefix docs-site run build`.
- Keep Mermaid source alongside the workflow it illustrates. Provide a title,
  text description, and links. Update the architecture explorer for confirmed
  changes to components or protocols and keep planned work in the roadmap.
- Update the API reference and Postman examples with controller or security
  changes. Reflect new Flyway migrations in the data model, and link ADRs to
  the affected diagrams and reference pages. Verify interactive changes in a
  browser with keyboard navigation and a narrow viewport.

## Secret prevention

- Keep local credentials in ignored files such as `.env` and
  `src/main/resources/application-local.yaml`. Check both the ignore rule and
  the Git index before relying on it.
- Use placeholders or synthetic values in versioned examples. Keep credential
  values out of reports and use redacted scanner output.
- Run Gitleaks on staged changes before committing and on the proposed commit
  range before opening a pull request. Address findings before proceeding.
  If a live credential was exposed, notify its owner so it can be revoked or
  rotated. Use the [local scanning commands](README.md#scan-for-secrets).

Read the relevant existing skill explicitly. Skill discovery varies by client.

- [Spring conventions](.github/skills/spring-microservice/SKILL.md)
- [Security](.github/skills/spring-security/SKILL.md)
- [Automated testing](.github/skills/automated-testing/SKILL.md)
