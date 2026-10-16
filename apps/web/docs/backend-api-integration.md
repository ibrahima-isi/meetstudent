# Backend API integration — handoff

**Written:** 2026-07-22
**Backend state:** `main` @ `f34ee3a` (PR #13 merged: media access control + school/course/program media FK reconciliation)
**Frontend state:** `customer_frontend` @ `49cd873` (Angular 20 + SSR + Tailwind 4)

> **Status (2026-10-16): §7 onwards (password reset, email verification, email
> change, profile password, account deletion, HTTP status corrections) describe
> the current behaviour; §0-§6 are the historical pre-work analysis.** Names such as `customer_frontend`, `backend/`
> and `environment.prod.ts` are obsolete (the repo is the `apps/` monorepo with a
> single `environment.ts`).
>
> **Status (2026-08-09): this is the pre-work analysis, not a current-state
> description.** It was written *before* `feat/backend-api-migration` and is
> kept for historical context. Since then, on that branch:
> - **§1 registration fix — DONE.** `register-form.component.ts` now sends
>   `confirmedPassword` and no `role`; `AuthService.register()` is typed with a
>   `RegisterPayload` interface instead of the payload described here.
> - **Update #107 (2026-10-15):** the no-`role` statement above is superseded.
>   Public registration now accepts `ROLE_STUDENT` or `ROLE_EXPERT` as a string;
>   an absent role defaults to student. Other roles return a field error.
> - **One rating per user per target (`V24`).** `POST /school-rates`, `/program-rates` and `/course-rates` now update the caller's existing rating in place (200, same `id`) instead of adding a row; the first rating is still 201. `RatingService.rateSchool/rateProgram/rateCourse` need no change (`HttpClient` treats 200 and 201 alike): re-rating just works. A lost insert race on the unique constraint is 409 `ErrorResponse`. A client-supplied `id` in the body is ignored (it can no longer overwrite another user's rating), and When re-rating, an absent/`null` `comment` keeps the previous comment and `""` clears it (the star-rating textarea has `maxlength="150"`). `comment` is limited to 150 characters (400 `{"comment": ...}`, it used to say 500 and fail at the database).
> - **§2's three "broken template bindings" — DONE.** All three now bind to
>   the resolved `cover`/`logo`/`photo` media objects.
> - **§2's `environment.prod.ts` instruction is wrong for this repo — there is
>   only a single `src/environments/environment.ts` and no `fileReplacements`
>   entry in `angular.json`.** `serverUrl` lives there only.
>
> Everything else below (§3 personal documents, §4 open questions, §5/§6
> process notes) has not been re-verified since this was written — treat it as
> a starting point, not a guarantee.

The backend API contract changed. This document lists every verified break, the
new contract, and a task-ordered plan. Every claim below was checked against
real code — file:line references are included so you can re-verify rather than
trust this document.

---

## 0. Backend prerequisite — ✅ FIXED (2026-07-22)

**CORS previously blocked the `Idempotency-Key` upload header.**

`WebSecurityConfig.corsConfigurationSource()` allowed only `authorization`,
`content-type`, `x-auth-token`, while `MediaController.upload` accepts an
`Idempotency-Key` header for safe retries — so browser preflight rejected it and
idempotent uploads were impossible from the SPA.

**Fixed and merged to both `dev` and `main`** (commit `7899a12`, pushed):
`idempotency-key` added to the allowlist, plus a new `CorsIntegrationTests`
covering allowed headers, rejected headers, and rejected origins.

Nothing to do here — idempotent uploads work against any current backend branch.

Also confirm `CORS_ALLOWED_ORIGINS` includes the dev origin. Default is
`http://localhost:4200` (`backend/src/main/resources/application.yml:32`), which
matches `ng serve`.

---

## 1. Registration is broken *today* (independent of the media work)

**Frontend sends the wrong field name and an ignored field.**

`src/app/features/auth/register-form/register-form.component.ts:128-142`:

```ts
const { password, confirmPassword } = this.step2Form.value;   // local check only
const userData = {
  ...this.step1Form.value,          // confirmPassword is NOT in step1Form
  password,
  role: { name: this.userType().toUpperCase() }
};
```

**Backend** (`UserController.java:48-56`) binds `RegisterRequest`, which requires
`confirmedPassword` (`@NotEmpty`), and calls `userService.registerStudent(...)`:

```java
public ResponseEntity<UserDTO> saveUser(@RequestBody @Validated RegisterRequest request) {
    if (!userService.isPasswordConfirmed(request.getPassword(), request.getConfirmedPassword())) {
        return ResponseEntity.badRequest().build();
    }
```

Two consequences:
1. `confirmedPassword` is never sent → **400 Bad Request** on every registration.
   (Note the spelling: `confirm**ed**Password`, not `confirmPassword`.)
2. `role` is silently ignored — registration always forces `ROLE_STUDENT`.
   Role changes now go only through `PATCH /api/v1/users/{id}/role`
   (`UserController.java:149`, ADMIN-only). Remove `role` from the payload; it is
   misleading dead data.

`RegisterRequest` accepts exactly: `firstname`, `lastname`, `email`, `password`,
`confirmedPassword`, `birthday`, `qualification`.

---

## 2. Entity images: URL strings → media FK + resolved object

School, Course, and Program no longer carry image URL strings.

| Entity | Removed | Request field (write) | Response field (read) |
|---|---|---|---|
| School | `logoUrl`, `coverPhotoUrl` | `logoMediaId`, `coverMediaId` (number) | `logo`, `cover` (MediaDTO) |
| Course | `photoUrl` | `photoMediaId` (number) | `photo` (MediaDTO) |
| Program | `photoUrl` | `photoMediaId` (number) | `photo` (MediaDTO) |

The contract is **asymmetric**: you *write* an id, you *read* an object.

### ProgramDTO: school and name search (ticket #118)

- `ProgramDTO` carries optional `schoolName` and `schoolCode` (both `null`/absent when the program has no school), next to `schoolId`. The programs catalogue (`/:lang/programs`) links the school name to `/:lang/schools/:schoolId`.
- `GET /api/v1/programs/name/{name}` sorts by `name,id` by default, so pages are stable. The client trims the term and `encodeURIComponent`s the path segment; an empty term lists all programs instead (`GET /programs`).
- `ProgramService` no longer invents `level` (`'Licence'`), `rating` (0) or `reviewCount` (0): what the API did not send stays absent. `rating` is the API's `averageRate`.

### CourseDTO: program and name search (ticket #119)

- `CourseDTO` carries optional `programName` next to `programId` (both `null`/absent when the course has no program). The courses catalogue (`/:lang/courses`) shows the program name as plain text: there is no public program page to link to.
- `GET /api/v1/courses` and `GET /api/v1/courses/name/{name}` sort by `name,id` by default (an explicit `?sort=` wins, an unknown property is 400). `?sortRate=most|less` sorts by the average rate first; that `name,id` (or explicit `sort`) order only breaks ties, so rate pages are stable too.
- `CourseService.searchCoursesByName` trims the term, `encodeURIComponent`s the path segment and answers an empty page, without a request, for a term containing `/ \ % ;` or equal to `.`/`..`; a blank term lists all courses.

### MediaDTO shape

```ts
interface MediaDTO {
  id: number;
  category: 'DIPLOMA' | 'CERTIFICATE' | 'BULLETIN' | 'PRESENTATION_VIDEO'
          | 'SCHOOL_LOGO' | 'SCHOOL_COVER' | 'COURSE_PHOTO' | 'PROGRAM_PHOTO' | 'USER_PHOTO';
  visibility: 'PUBLIC' | 'PRIVATE';
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | null;
  rejectionReason: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  publicUrl: string | null;   // set ONLY for PUBLIC media
}
```

### ⚠️ `publicUrl` is relative and NOT under `/api/v1`

`MediaMapper` returns `"/uploads/" + storageKey`, e.g. `/uploads/public/ab12.jpg`.
Static files are served at the **server root** (`/uploads/public/**`), while
`environment.apiUrl` is `/api/v1` (relative: the reverse proxy, or `proxy.conf.json` under `ng serve`, forwards it to the API).

Naively prefixing `apiUrl` yields a broken `…/api/v1/uploads/…`. Add a separate
origin to `src/environments/environment*.ts`:

```ts
export const environment = {
  production: false,
  apiUrl: '/api/v1',
  serverUrl: '',                           // NEW — server root; SSR overrides it via SERVER_URL
  mediaBaseUrl: '',                        // NEW — prefix for Media.publicUrl (/uploads/**)
};
```

### Broken template bindings (will render empty images)

- `src/app/features/student/home-page/home-page.component.html:112` — `school.coverPhotoUrl`
- `src/app/features/student/school-detail-page/school-detail-page.component.html:29` — `school().coverPhotoUrl`
- `src/app/features/public/schools-page/schools-page.component.html:136` — `school.coverPhotoUrl`

All become `undefined`. Replace with the resolved media, e.g.
`school.cover?.publicUrl` run through a helper that prefixes `serverUrl`.

### Upload flow (ADMIN only for these categories)

```
1. POST /api/v1/media?category=SCHOOL_LOGO   (multipart, field name "file")
   → 201 { id: 42, publicUrl: "/uploads/public/ab12.jpg", ... }
2. PUT/PATCH /api/v1/schools/{id}  { logoMediaId: 42 }
   → 200 { logo: { id: 42, publicUrl: ... }, ... }
```

`SCHOOL_LOGO`, `SCHOOL_COVER`, `COURSE_PHOTO`, `PROGRAM_PHOTO` are all
**ADMIN-only** uploads. The customer app likely never uploads these — it only
*reads* them. Confirm before building upload UI here (this may belong in
`bo_frontend` instead).

---

## 3. Personal documents are now private media (biggest change)

`UserDTO` (`backend/.../dto/UserDTO.java:40-42`) now returns:

```java
private List<MediaDTO> diplomas;        // was List<String>
private List<MediaDTO> certificates;    // was List<String>
private MediaDTO presentationVideo;     // was String presentationVideoUrl
```

Frontend model `src/app/models/entities.ts:33-35` still declares
`diplomas?: string[]`, `certificates?: string[]`, `presentationVideoUrl?: string`
— now structurally wrong. `src/app/services/user.service.ts:37-38` passes them
through as arrays, so any template doing `{{ diploma }}` will print `[object Object]`.

### ⚠️ Private files cannot be loaded with a plain `<img src>`

Private media has `publicUrl: null` and is reachable only via
`GET /api/v1/media/{id}`, which enforces **owner-or-admin**
(`MediaService.getAccessibleMedia`). The route is `permitAll` in
`WebSecurityConfig.java:56` — authorization happens in the controller, not the
filter chain — so an unauthenticated request gets **403**, not a login redirect.

A browser `<img src="…/api/v1/media/5">` sends **no Authorization header**, so it
will 403. You must fetch the blob through `HttpClient` (the JWT interceptor at
`src/app/interceptors/jwt.interceptor.ts:12` attaches the token) and convert:

```ts
this.http.get(`${environment.apiUrl}/media/${id}`, { responseType: 'blob' })
  .pipe(map(blob => URL.createObjectURL(blob)));
```

Remember to `URL.revokeObjectURL()` on destroy to avoid leaking memory.

Server sends `Content-Disposition: inline` only for jpeg/png/webp/pdf; everything
else is forced to `attachment`, plus `nosniff` and a sandbox CSP.

### Other media endpoints

| Endpoint | Access | Use |
|---|---|---|
| `POST /api/v1/media?category=…` | role-gated per category | upload (multipart, field `file`) |
| `GET /api/v1/media/{id}` | public media: anyone; private: owner/admin | download |
| `GET /api/v1/media/mine` | authenticated | list own media + verification status |
| `GET /api/v1/media?status=PENDING` | ADMIN | moderation queue |
| `PATCH /api/v1/media/{id}/verification` | ADMIN | set VERIFIED/REJECTED |
| `DELETE /api/v1/media/{id}` | owner/admin | delete |

Students/experts may upload `DIPLOMA`, `CERTIFICATE`, `BULLETIN`,
`PRESENTATION_VIDEO`, and `USER_PHOTO`. These start `PENDING` and an admin
verifies them; **status is informational — a `REJECTED` doc is not blocked.**
Surface `verificationStatus` in the profile UI.

---

## 4. Smaller notes / open questions

- **`UserDTO.photoUrl` is still a plain `String`** while a `USER_PHOTO` media
  category exists. The user avatar was *not* migrated to the FK model. Confirm
  intended behavior before wiring avatar upload — this looks like an unfinished
  seam in the backend, not something to work around blindly.
- **`SchoolDTO` has no `accreditations`.** `src/app/services/school.service.ts:45`
  does `accreditations: school.accreditations || []`, which silently yields `[]`
  forever. Pre-existing, unrelated to this migration — flag it, don't silently "fix"
  it by inventing an endpoint.
- **Uncommitted change** in the frontend repo:
  `src/app/features/student/home-page/home-page.component.html` (1 line). Review
  or stash before starting so it doesn't get tangled into the migration commits.
- `customer_frontend/.claude/CLAUDE.md` mentions a nested React/Vite app under
  `meetstudent/`; it does not exist in the current tree. Ignore that instruction.

---

## 5. Suggested task order

Each task should build (`npm run build`) and keep tests (`npm test`) green.

1. **Types first** — update `src/app/models/entities.ts`: add `MediaDTO`; change
   School/Course/Program image fields; change User document fields. Compiler errors
   then become your worklist.
2. **Config** — add `serverUrl` to both `environment.ts` and `environment.prod.ts`;
   add a small `MediaUrlService`/pipe that turns `publicUrl` → absolute URL and
   returns a fallback when `null`.
3. **Fix registration** — send `confirmedPassword`, drop `role`. Verify a real
   signup returns 201 (this is broken in production today, so it is the highest
   user-facing value and is independent of everything else).
4. **Public images** — repoint the three template bindings to `cover`/`logo`/`photo`
   via the helper. Verify against a real backend that images render.
5. **Private documents** — blob-fetch flow for diplomas/certificates/video in the
   profile page; show `verificationStatus`; revoke object URLs on destroy.
6. **Personal document upload** (if in scope) — multipart POST with `category`,
   optional `Idempotency-Key` *only if* the backend CORS fix from §0 has landed.
7. **Decide on admin upload UI** — school/course/program image upload is ADMIN-only
   and probably belongs in `bo_frontend`, not this app.

Do **not** start by editing templates; the type layer drives everything else.

---

## 6. Verify against a running backend

The backend is on `main` and green (77 unit + 89 integration tests).

```bash
cd ../backend && ./mvnw spring-boot:run     # dev profile, Neon cloud DB
```

Neon is the intended dev database — running against it is expected and safe.
Swagger: http://localhost:8080/swagger-ui.html — use it to confirm live response
shapes before coding against them.

---

## 7. Password reset (web side)

- `POST /auth/password-reset/request` `{email, lang}` always answers 202 for a
  well-formed address, so `/:lang/forgot-password` shows one neutral sentence
  whatever the account state; 400 `{email}` becomes our own field message.
- `POST /auth/password-reset/confirm` `{token, password, confirmedPassword}` → 204;
  400 `{token}` (expired/consumed/unknown, one message for all), `{password}`,
  `{confirmedPassword}`. The API's text is never displayed, the front has its own.
  For all roles, including admins, success revokes prior access/refresh sessions
  and outstanding reset/email-change links; only the new password signs in.
  Admin recovery survives application restarts (#101).
- `GET /auth/capabilities` → `{emailEnabled}`, cached in `AuthService.getCapabilities()`;
  any failure reads as `false`.
- Reset mails link to `/<lang>/reset-password#token=…`. The token is in the
  **fragment** so it never reaches a server or access log: the page is
  client-rendered, reads it once into memory, strips it from the address bar
  (`replaceUrl`) and sends `noindex` + `referrer: no-referrer`. `public/robots.txt`
  disallows these pages.
- `refreshInterceptor` now skips only `/auth`, `/auth/refresh` and
  `/auth/password-reset/*`; other authenticated calls under `/auth` are refreshed on 401.

## 8. Email verification (web side)

- `POST /auth/email-verification/confirm` `{token}` → 204; 400 `{token}` for any bad
  link (one message for unknown, expired, used). Mails link to
  `/<lang>/verify-email#token=…`; same handling as the reset link (fragment read
  once into memory, stripped from the URL, `noindex` + `no-referrer`, client-rendered).
- `POST /auth/email-verification/resend?lang=fr|en` (Bearer; the query `lang` is the locale being read, so the mail matches it) → 202; 409 already verified,
  429 cooldown/daily cap, 503 mail disabled. The banner in `PublicShellComponent`
  shows only when signed in, `user.emailVerified === false` and
  `capabilities.emailEnabled`; dismissal is kept in `sessionStorage`.
- `User.emailVerified` is optional on the client: only an explicit `false` counts
  as unverified. After a successful confirmation (or a 409) the cached user is
  reloaded with `GET /users/id/{id}`.
- `POST /users` now also sends `lang` (`fr`|`en`, the locale being read); the
  login notice after registration mentions the confirmation email only when
  `emailEnabled`.

## 9. Changing the email address (web side)

- The address no longer changes through `PATCH /users/{id}`: a different `email`
  there is 400 `{email}` (the same one, in any case, is ignored), so the profile
  form has no email field and `ProfileUpdate` no longer has `email`.
- `ProfileEmailSectionComponent` (profile page) is the one place for it:
  - `POST /users/{id}/email-change` `{newEmail, currentPassword, lang}` → 202;
    400 `{currentPassword}` / `{newEmail}` (shown under their field, API text as is),
    429 cooldown/daily cap, 503 mail off. The account keeps its current address and
    keeps signing in with it; the new one is only *pending*.
  - `GET /users/{id}/email-change` → `{pendingEmail}` (absent when nothing is
    pending); `DELETE` cancels (204); `POST …/resend?lang=` mails the link again
    (202, 409 nothing pending, 429).
  - Owner only (403 for anyone else, admins included).
- The link mailed to the **new** address opens `/<lang>/verify-email#token=…&type=change`
  (the same page as verification; `type=change` selects the endpoint and the wording).
  `POST /auth/email-change/confirm` `{token}` → 204; 400 `{token}` for any bad link
  (unknown, expired, used, or the address was taken meanwhile). Success increments
  the credential version and revokes every access/refresh session. The confirmation
  page clears local tokens and cached identity, then invites re-login with the new
  address in the active locale. Ordinary email verification still refreshes the
  cached user without ending the session.
- The same section shows whether the current address is confirmed and offers the
  confirmation email again (`POST /auth/email-verification/resend`), so dismissing
  the banner never loses the way to confirm.

## 10. Profile password change (#100, 2026-10-13)

- `PUT/PATCH /users/{id}` requires `currentPassword` when a nonempty `password`
  is supplied for one's own account. Missing/wrong values return 400
  `{currentPassword}` without applying any profile changes. An administrator
  editing another account is exempt; an administrator editing themself is not.
- A successful password change still returns 200 with the saved profile, but
  invalidates all previous access/refresh credentials, including the caller.
  The profile clears local authentication and navigates to
  `/:lang/login?passwordChanged=1`, where a translated notice explains re-login.
  Ordinary profile edits do not sign out and omit both password fields.
- Open reset and email-change links are invalidated. A request/resend racing a
  password change can return 409 rather than recreate a cancelled email link.
- Old JWTs without the new credential version remain compatible at version zero
  until the first password change/reset or their original expiry. Reset-triggered
  revocation is delivered in #101; email-confirmation revocation and per-device
  inactivity remain in #102 and #103 respectively.

## 11. Account deletion (#104, 2026-10-14)

- `DELETE /users/{id}` accepts an optional JSON body `{currentPassword}`. For
  self-deletion, including an administrator deleting their own account, the
  current password is required. An administrator deleting another account does
  not need it.
- Success returns an empty 204 response. A missing or incorrect current password
  returns 400 `{currentPassword}`; authentication and authorization failures use
  the usual 401/403 responses, and an absent account returns 404. A client that
  invokes this endpoint should show the password field error and handle 204
  without expecting a user object.
- Deletion revokes the account's sessions and action tokens and removes dependent
  ratings. Neither the web app nor the backoffice currently offers a user-account
  deletion journey or calls this endpoint.

## HTTP status corrections (fix/api-remaining-status-codes)

- Unknown `?sort=` property: 400 `ErrorResponse` (was 500). Unacceptable `Accept` header: 406 with a JSON `ErrorResponse` body.
- `POST/PUT/PATCH /schools` with an unknown tag id: 400 `{"tags": "Unknown tag <id>"}` (was 500).
- Account deletion removes the user's stored files only after the database commit, best-effort.
