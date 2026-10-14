# Ticket #101 — verification evidence (2026-10-14)

Base: `origin/dev` at `5b37b2a`. Isolated worktree `f21e`, branch `fix/password-reset-all-roles`. No source worktree was imported or modified.

## Red → green

| Regression | Observed failure | Result after fix |
| --- | --- | --- |
| Reset for STUDENT, EXPERT and ADMIN | Old JWT returned 200 instead of 401 in all three cases | All old access/refresh credentials refused; only new password signs in |
| Pending email change after reset | Confirmation returned 204 instead of 400 | Link refused; original address retained |
| Admin bootstrap environment rotation | Existing password replaced at reboot | Existing password preserved |
| Seed reuse / student address | Changed seed account reused or non-admin promoted | Changed account preserved; non-admin bootstrap refused |
| Seed credential revocation | Old seed JWT remained usable | Credential version incremented; sessions and action links refused |
| Backoffice recovery entry | Link absent | Link reaches public `/fr/forgot-password` |
| Recovery configuration | `webUrl` ignored; unsafe URLs accepted | Configured public origin used; non-HTTP(S), credential/query/fragment URLs rejected |

Reset/link tests use real committed versioned HTTP requests and captured mail, with actual login/access/refresh credentials. Nine deterministic JDBC-boundary race cases cover login, refresh and email request/resend against password change/reset, plus two confirmations of the same reset link. The second confirmation cannot overwrite the winning password. Existing expiry, single-use, field-validation and neutral-response/rate-limit tests remain green.

The production-profile restart test closes one full Spring application context after admin reset, starts another against the same database and original bootstrap configuration, then checks login, old access/refresh rejection and old reset/email-change-link rejection through HTTP. It sends no real mail.

## Local verification

| Check | Command | Result |
| --- | --- | --- |
| API full | `cd apps/api && ./mvnw clean verify -Dspring.jpa.show-sql=false` | 226 unit + 286 integration tests; zero failures/errors/skips |
| PostgreSQL migrations/schema | Production Flyway location, `ddl-auto=validate` | 20 migrations applied; V20 schema validated |
| PostgreSQL reset/concurrency HTTP | Command below | 38 tests: 29 reset + 9 deterministic races; zero failures/errors/skips |
| Web full | `cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless` | 927 passed |
| Backoffice full | `cd apps/backoffice && npm test -- --no-watch --browsers=ChromeHeadless` | 275 passed |
| Builds | `npm run build` in web and backoffice | Both passed, including web SSR |
| Backoffice configuration script | `sh -n` and temporary execution/JSON parsing | Passed |
| Diff | `git diff --check` | Passed |

```sh
./mvnw verify -Dtest=PasswordResetServiceTest \
  -Dit.test=PasswordResetIntegrationTests,PasswordSessionRaceIntegrationTests \
  -Dspring.datasource.url=jdbc:postgresql://localhost:55432/ms101 \
  -Dspring.datasource.username=ms101 -Dspring.datasource.password=disposable-ms101 \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver \
  -Dspring.flyway.enabled=true \
  -Dspring.flyway.locations=filesystem:src/main/resources/db/migration \
  -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

The explicit migration location excludes the separate H2 test V1. PostgreSQL 18 emitted Flyway's existing support-version advisory; migrations, schema validation and the HTTP suite passed.

Browser smoke used the actual production builds served temporarily at ports 14300 (backoffice) and 14200 (web). Activating “Mot de passe oublié ?” reached the French recovery form, changing locale reached the English form and retained the localized login link, and an absent reset token displayed the translated invalid-link screen with a working new-link destination. Mobile viewport 390×844 showed the backoffice link and complete recovery form without horizontal overflow. Mail remained disabled; no reset form was submitted through the browser.

## Review

Standards: zero violations or confirmed security defects. Optional heuristic: revocation policy occurs in bootstrap, profile and reset paths; their different token policies currently justify leaving these implementations local.

Spec: one stale deployment-guide paragraph contradicted access-token revocation. Corrected before delivery; no functional gap or scope creep found. The independent reviews used Sol and the pinned baseline `5b37b2a`.

## Resources and delivery

`docker compose -p ms101 -f /tmp/ms101-compose.yml down -v` removed the disposable PostgreSQL container/network after tests. Both temporary browser servers were stopped and the test tab closed. No real SMTP, production deployment, main promotion or unrelated worktree cleanup occurred. PR/CI/squash/branch-cleanup evidence is recorded in completion tracking after delivery.
