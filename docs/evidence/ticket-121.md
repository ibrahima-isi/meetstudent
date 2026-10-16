# Ticket #121 — Cherchez

## Scope and implementation

Issue [#121](https://github.com/ibrahima-isi/meetstudent/issues/121), parent [#99](https://github.com/ibrahima-isi/meetstudent/issues/99). Base `d73428d`, isolated branch `feat/search-entry-121`. Owner explicitly authorized the exact seven-file manifest and the proposed form on 2026-10-16.

The first discovery card now contains a native resource select, labelled search field and Cherchez/Search submit button. Schools are selected initially; programmes and courses are explicit choices. Reactive controls feed the existing localized public catalogue routes. A trimmed nonempty query travels as `q`; blank input opens the selected catalogue. No additional query-length restriction, catalogue implementation, HTTP contract or authorization change was introduced. Existing theme tokens and field/button classes are reused.

## Automated evidence

- Red before implementation: focused ChromeHeadless run failed the new rendered-form regression because no search field existed; 9 existing specs passed. Log `/private/tmp/121-red.log`.
- First form iteration exposed missing native submit handling; corrected with preventDefault and the existing router navigation interface.
- Green focused run: **22/22**. Log `/private/tmp/121-green3.log`.
- The same rendered-component/router/HTTP seams approved in #99 exercise all three real catalogue components with real services and HTTP contract fixtures: fr/en labels, native select/input/button submission, locale/query preservation including accents and reserved `&`, loading before HTTP completion, matching result rendering, HTTP 500, retry of the same resource/query, empty results, and plain catalogue navigation for blank input.
- Full affected web ChromeHeadless suite: **1063/1063**. Log `/private/tmp/121-full.log`.
- Application and test TypeScript checks: `tsc --noEmit -p tsconfig.app.json` and `tsc --noEmit -p tsconfig.spec.json` passed.
- Production SSR build: passed, 13.015 seconds; output `apps/web/dist/frontend`. Log `/private/tmp/121-build.log`.
- `git diff --check`: passed.

HTTP fixture tests prove the frontend contract and state transitions; they do not by themselves prove a running backend/browser session. The coordinator owns the real PostgreSQL/API/browser verification, independent Standards/Spec review, final-head CI, PR, squash, closure and cleanup. These remain pending in this record until their actual results are added.

## Browser handoff

Run this branch’s production SSR server against the dedicated API. On `/fr` and `/en`, scroll to `#how-it-works`; use the visible type selector, name field and Cherchez/Search button for each resource type. Confirm the destination locale and `q`, matching API fixture names, native keyboard/Enter submission, and mobile 375px/light/dark presentation. Use a no-match term and retry after a temporary API failure. Empty search must reach the selected plain catalogue. Existing results views remain responsible for all loading, empty and retry states.
