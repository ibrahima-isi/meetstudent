# Implementation completion

## Hide missing programme duration

- [x] Hide the whole duration row for missing, null, zero or negative duration; preserve positive-duration translations.
- [x] Align Program.duration typing with the nullable, optional API value.
- [x] Verify failing regression first, then full ChromeHeadless tests and Angular build.
- [ ] Open PR against dev, squash-merge after green CI, delete local branch.

Verification: before the fix, all four regression cases failed (43 existing specs passed). After the fix, `npm test -- --no-watch --browsers=ChromeHeadless` passed all 924 tests and `npx ng build` succeeded. Existing French/English singular and plural checks passed.

Next: publish the PR, wait for green CI, squash-merge into dev, then delete the local working branch.
