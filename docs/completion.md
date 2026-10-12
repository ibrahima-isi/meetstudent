# Implementation completion

## Hide missing programme duration

- [x] Hide the whole duration row for missing, null, zero or negative duration; preserve positive-duration translations.
- [x] Align Program.duration typing with the nullable, optional API value.
- [x] Verify failing regression first, then full ChromeHeadless tests and Angular build.
- [x] Open [PR #97](https://github.com/ibrahima-isi/meetstudent/pull/97) against dev. CI and merge status are tracked on the PR.

Verification: before the fix, all four regression cases failed (43 existing specs passed). After the fix, `npm test -- --no-watch --browsers=ChromeHeadless` passed all 924 tests and `npx ng build` succeeded. Existing French/English singular and plural checks passed.

Handoff: implementation and local verification are complete. Check PR #97 for delivery status before continuing with the next ticket. The working branch is `fix/hide-missing-programme-duration`; squash merge is gated by green CI, followed by local branch deletion.
