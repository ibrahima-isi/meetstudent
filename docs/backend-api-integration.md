# Backend API integration — handoff

**Written:** 2026-07-22
**Backend state:** `main` @ `f34ee3a` (PR #13 merged: media access control + school/course/program media FK reconciliation)
**Frontend state:** `customer_frontend` @ `49cd873` (Angular 20 + SSR + Tailwind 4)

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
`environment.apiUrl` is `http://localhost:8080/api/v1`.

Naively prefixing `apiUrl` yields a broken `…/api/v1/uploads/…`. Add a separate
origin to `src/environments/environment*.ts`:

```ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  serverUrl: 'http://localhost:8080',      // NEW — for /uploads/** and media blobs
};
```

### Broken template bindings (will render empty images)

- `src/app/features/student/home-page/home-page.component.html:112` — `school.coverPhotoUrl`
- `src/app/features/student/school-detail-page/school-detail-page.component.html:29` — `school().coverPhotoUrl`
- `src/app/features/public/landing-page/landing-page.component.html:136` — `school.coverPhotoUrl`

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
