# Ticket #121 — Cherchez

## Scope and implementation

Issue [#121](https://github.com/ibrahima-isi/meetstudent/issues/121), parent [#99](https://github.com/ibrahima-isi/meetstudent/issues/99), [PR #157](https://github.com/ibrahima-isi/meetstudent/pull/157) into `dev`. Initial isolated base `d73428d`; updated from `origin/dev` at `b0dbba8` (documentation-only #156). Branch `feat/search-entry-121`. Owner explicitly authorized the form and exact seven-file manifest on 2026-10-16. Shared checkout changes and other worktrees were preserved.

The first discovery card contains a native resource selector, labelled search field and Cherchez/Search button. Schools are selected initially; programmes and courses are explicit choices. Reactive controls feed the existing localized public catalogue routes. A trimmed nonempty query travels as `q`; blank input opens the selected catalogue. Existing catalogue components, HTTP contracts, authorization and theme tokens are reused.

The existing name-search path contracts cannot carry `/`, `\`, `%`, `;`, or whole `.` / `..` terms. The new entry explains that limit in French/English before navigation rather than letting the existing services fabricate an empty page. It retains query/type, focuses the field and associates a visible alert with `aria-invalid`/`aria-describedby`; correcting the input clears the error. Internal dots, accents and `&` remain accepted. No new length restriction, API change or unrelated ticket implementation was introduced.

## TDD and automated verification

- Initial red: new rendered-form regression failed because no search field existed; **1 failed, 9 passed** (`/private/tmp/121-red.log`).
- Initial green: **22/22** focused rendered-component/router/HTTP tests; **1063/1063** full web tests, app/spec types and production SSR build passed.
- Independent Spec review found the inherited unsupported-query false-empty behavior reachable through the new form. Correction red reproduced navigation with `100% anglais` and loss of the form: **1 failed, 22 passed** (`/private/tmp/121-review-red.log`).
- Corrected final code: **29/29** focused tests and **1070/1070** full web tests; application and test TypeScript checks, production SSR build and diff whitespace check passed (`/private/tmp/121-review-{focused,full,types-app,types-spec,build}.log`).
- Tests use the approved rendered Angular component/router and public HTTP seams, real catalogue components/services and controlled contract fixtures. They cover three destinations in fr/en, labels and native submission, locale/query preservation, accents and reserved `&`, loading, results, HTTP failure/retry, empty results, blank query and explicit unsupported-input validation.
- Full API Maven verification: **630 tests** = 239 unit + 391 integration, zero failures/errors/skips (`/private/tmp/meetstudent-121-api-verify.log`).
- Backoffice: **300/300** tests and production build passed (`/private/tmp/meetstudent-121-backoffice-{test,build}.log`). API/backoffice sources were unchanged by #121.

## Real browser and PostgreSQL verification

Production SSR build with a dedicated prod-profile API, disposable PostgreSQL 18 and same-origin proxy; mail disabled. Flyway history query confirmed **24 successful migrations, versions 1–24**; JPA `ddl-auto=validate` boot succeeded. Three disposable resources were created through authenticated public HTTP endpoints, not fabricated marketing data.

- **24 real search journeys** = schools/programmes/courses × fr/en × 1440px desktop/375px mobile × light/dark. Trimmed `q`, native Enter on desktop and button activation on mobile reached the right localized catalogue and displayed the corresponding real HTTP 200 result.
- **8 layout checks**: visible associated labels, native focus, no horizontal overflow. Mobile dark and desktop light captures inspected visually.
- **3 empty searches** returned real HTTP 200 with zero results. **3 one-shot network faults** exercised the error/retry UI; each retry recovered through the real API and displayed its resource. Fault injection was limited to testing failure behavior.
- **0 JavaScript page errors** in the 24-journey matrix (`/private/tmp/meetstudent-121-browser-results.json`).
- Additional real authenticated session: landscape **812×375**, select→input Tab order, course search and direct catalogue reload passed (`/private/tmp/meetstudent-121-browser-extra.log`).
- After the review correction and rebuilt SSR: **12 validation/recovery journeys** = three types × fr/en × desktop/mobile. Unsupported input stayed on the form without a name-search request, retained query/type/focus, displayed translated ARIA-associated error and cleared it on correction; the corrected query then displayed the real API result. No horizontal overflow (`/private/tmp/meetstudent-121-browser-validation-results.json`). Mobile English dark error capture inspected visually.

Both smoke runs were cleaned: only their own API, SSR/proxy processes and `ms121-pg` container/anonymous volume were removed. Ports 18121/42121/42122 had no listeners and container inventory for `ms121` was empty. The pre-existing shared stack was not touched.

## Independent review and delivery

Exactly three primary phase agents were used with the requested models/efforts: reader gpt-5.6-sol medium, planner/implementer gpt-6.1-sol medium, reviewer/fixer gpt-6.1-sol high. The reviewer ran independent Standards and Spec child agents in separate contexts.

- Initial Standards: **0** documented violations / actionable smells.
- Initial Spec: **1 P2**, inherited unsupported-query behavior reached through the new entry; corrected in signed commit `e04d056`.
- Final independent review on signed integration head `83af31f`: **Standards 0 / Spec 0 remaining**. Signed implementation `834ffe0`; signed merge of current `dev` `83af31f`.

The first [API/web/backoffice CI](https://github.com/ibrahima-isi/meetstudent/actions/runs/38973287376) passed on `83af31f4778201dd336523caf03aef50dfc52085`. The final documentation head must independently pass all three checks before squash; earlier green checks do not qualify a later head. Final documentation, exact-head CI, squash verification, explicit issue closure and worktree/branch cleanup are owned by the coordinator. The PR provides the authoritative final CI/merge state; the exported delivery record and successor handoff retain exact final hashes and cleanup results after merge.
