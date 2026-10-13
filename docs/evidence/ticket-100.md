# Ticket #100 — verification evidence (2026-10-13)

Base: `origin/dev` initially `b236bc1`; docs-only update `f90c104` integrated before delivery. No changes to existing source worktrees. Commands run inside the isolated worktree with Java 21 and ChromeHeadless 152.

## Red → green observations

| Regression | Observed failure before correction | Verified result |
| --- | --- | --- |
| Current password missing | `Status expected:<400> but was:<200>` | Refused without profile modification |
| Old JWT after change | `Status expected:<401> but was:<200>` | Access and refresh rejected; new login accepted |
| Outstanding email link | `Status expected:<400> but was:<204>` | Email and reset links rejected |
| Current-password form field | Unexpected PATCH with missing current password | No PATCH; inline error |
| Successful password form | Session retained; no login navigation | Session cleared; localized login |
| Login notice | Missing French/English notice | Both languages verified |
| Accessible field error | Missing referenced error element | Associated inline error; session retained |
| Email request/resend race | Two deterministic cases returned 202 | Both refuse obsolete request after password-change commit |
| Login race sensitivity | Removing version comparison caused one failure | Restored check passes; mutation not retained |

The first complete API run exposed loose ModelMapper conversion of `password` to `passwordVersion`, H2 schema-default mismatch and old password-update fixtures. Explicitly exclude the server-owned version from mapping, align the generated default to V20 and provide currentPassword in the existing valid-update fixtures. The final full run below includes these fixes.

## Final local checks

| Check | Command | Result |
| --- | --- | --- |
| API, full | `cd apps/api && ./mvnw clean verify -Dspring.jpa.show-sql=false` | 226 unit + 273 integration tests, zero failures/errors/skips; BUILD SUCCESS |
| Web, full | `cd apps/web && npm test -- --no-watch --browsers=ChromeHeadless` | 927 passed |
| Web build | `cd apps/web && npm run build` | Production SSR build passed |
| TypeScript | `cd apps/web && npx tsc --noEmit -p tsconfig.app.json` | Passed |
| Backoffice, full | `cd apps/backoffice && npm test -- --no-watch --browsers=ChromeHeadless` | 271 passed |
| Backoffice build | `cd apps/backoffice && npm run build` | Passed |
| PostgreSQL upgrade | Flyway V1–V19; insert legacy user/refresh rows; Flyway V20 | Both rows preserve version zero, 20 migrations validated |
| PostgreSQL HTTP | AuthIntegrationTests + PasswordSessionRaceIntegrationTests with Flyway and `ddl-auto=validate` | 15 passed (11 auth + 4 deterministic races) |
| Cleanup | `docker compose -p ms100 -f /tmp/ms100-compose.yml down -v` | Disposable PostgreSQL container/network removed |

PostgreSQL HTTP command (substitute a disposable database's connection values):

```sh
./mvnw verify -Dtest=UserTokenServiceTest   -Dit.test=AuthIntegrationTests,PasswordSessionRaceIntegrationTests   -Dspring.datasource.url=jdbc:postgresql://localhost:55432/ms100   -Dspring.datasource.username=ms100 -Dspring.datasource.password=disposable-ms100   -Dspring.datasource.driver-class-name=org.postgresql.Driver   -Dspring.flyway.enabled=true   -Dspring.flyway.locations=filesystem:src/main/resources/db/migration   -Dspring.jpa.hibernate.ddl-auto=validate -Dspring.jpa.show-sql=false
```

The explicit Flyway location excludes the separate legacy H2 test migration. The refresh test reuses ROLE_STUDENT instead of inventing ROLE_REFRESH, so it exercises the real seeded role model. The unrelated seeded role-sequence repair remains in the later API/registration tickets. Flyway emitted its existing PostgreSQL 18 support-version advisory; migration and schema validation nevertheless passed on PostgreSQL 18.6.

## Standards

No documented-standard violation or confirmed regression remains in the reviewed diff. Non-blocking heuristic: the private profile service combines role-edit and current-password-exemption booleans; simplify if its interface grows. No unrelated refactor requested.

## Spec

One initial finding: concurrent email-link creation/resend could resurrect a cancelled request. Reproduced in two deterministic HTTP tests, fixed with user locking plus version revalidation. Action-token issuance/consumption now uses a consistent lock order. Final re-review: no actionable finding within #100.

Review outcome: Standards 0 blocking findings (1 heuristic); Spec 0 remaining findings (1 corrected). Static review complements the execution evidence above. No real SMTP, deployment, main promotion or unrelated worktree cleanup was performed. CI and merge evidence is linked from completion tracking and the PR.

CI: [run 38413822495](https://github.com/ibrahima-isi/meetstudent/actions/runs/38413822495), commit `ccb655a49c050be21029f9e02d73964f84bc4c42`: API passed (1m54s), web passed (1m3s), backoffice passed (41s). Final documentation closeout is checked again before squash.
