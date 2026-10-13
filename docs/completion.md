# Implementation completion

## Hide missing programme duration

- [x] Hide the whole duration row for missing, null, zero or negative duration; preserve positive-duration translations.
- [x] Align Program.duration typing with the nullable, optional API value.
- [x] Verify failing regression first, then full ChromeHeadless tests and Angular build.
- [x] Open [PR #97](https://github.com/ibrahima-isi/meetstudent/pull/97) against dev. CI and merge status are tracked on the PR.

Verification: before the fix, all four regression cases failed (43 existing specs passed). After the fix, `npm test -- --no-watch --browsers=ChromeHeadless` passed all 924 tests and `npx ng build` succeeded. Existing French/English singular and plural checks passed.

Handoff: implementation and local verification are complete. Check PR #97 for delivery status before continuing with the next ticket. The working branch is `fix/hide-missing-programme-duration`; squash merge is gated by green CI, followed by local branch deletion.


## Spec #99 — persistent workstream checklist

Track individual deliveries in [../completion.md](../completion.md). Preserve this workstream list until all its child tickets pass their own verification; implementing #100 alone does not complete point 1.

- [ ] 1. Credential/session security (#100–104).
- [ ] 2. Reliable API mutations and backoffice behavior (#105–106, deletion in #104).
- [ ] 3. Student/teacher registration, wishlist and unique ratings (#107–112).
- [ ] 4. Engineering-skills worktree cleanup (#113).
- [ ] 5. Safe auto-login worktree cleanup (#114).
- [ ] 6. Hide authenticated landing signup band (#115).
- [ ] 7. Footer account buttons (#117).
- [ ] 8. Four footer product destinations (#118–120, #122).
- [ ] 9. School/programme/course comparison (#123–125).
- [ ] 10. Search entry across three resource types (#121).
- [ ] 11. School/programme rankings (#126–127).
- [ ] 12. Découvrir / Discover navigation (#128).
- [ ] 13. Exactly one About footer (#116).
- [ ] 14. Exactly one Contact footer (#116).

### #100 — password change and session revocation (2026-10-13)

Implementation: current password required on self-service PUT/PATCH, administrator exemption only for another account; monotone persisted credential version; access/refresh revocation including the caller; reset/email-change links consumed; historical version-zero compatibility; serialized login/refresh; translated profile errors and re-login notice. ADR: [0001-credential-session-version.md](adr/0001-credential-session-version.md).

Tests use the approved HTTP and rendered Angular form boundaries. The two race cases use actual committed requests and a JDBC read barrier, not timing-only concurrent starts. Removing the login version check causes the regression test to fail; restoring it passes.

Local verification complete. Detailed commands and regression evidence: [ticket-100.md](evidence/ticket-100.md).

Evidence:

| Check | Before | After |
| --- | --- | --- |
| Missing current password | HTTP 200 instead of 400 | 400; profile/session preserved |
| Old access token after password change | HTTP 200 instead of 401 | 401; old refresh rejected; new login works |
| Pending email-change link | HTTP 204 instead of 400 | 400, as for old reset links |
| Form current-password requirement | Unexpected PATCH | No request; inline validation |
| Successful form change | Session retained | Session cleared; localized login navigation |
| Login notice / accessible field error | Missing notice / missing associated error | Both translated notice and associated error rendered |
| Angular full suite | — | 927 tests passed; production SSR build passed |
| PostgreSQL migration | V19 with legacy user/refresh rows | V20 applied; both rows retain version zero |

Final local checks: API 226 unit + 273 integration tests passed; web 927 and backoffice 271 tests passed; both production builds and TypeScript checking passed. PostgreSQL: V19 → V20 plus 15 HTTP/race tests passed with schema validation. Standards/Spec review completed; the email-link race finding was reproduced and corrected. Disposable PostgreSQL removed.

- [ ] [#100 — Changer son mot de passe et révoquer les anciennes sessions](https://github.com/ibrahima-isi/meetstudent/issues/100): implementation and local verification complete; PR/CI delivery pending.

Delivery remains pending green PR CI and squash merge. No main promotion or unrelated worktree cleanup is part of this ticket (#129 owns the final promotion). Existing source branches/worktrees and their uncommitted files are preserved.
