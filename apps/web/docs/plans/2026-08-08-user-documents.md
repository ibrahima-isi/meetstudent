# User Private Documents Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a student/expert see, open, upload, and delete their own private documents (diplomas, certificates, bulletins, presentation video) in the profile page.

**Architecture:** A standalone `UserDocumentsComponent` embedded in the profile page. It reads the user's own media from `GET /api/v1/media/mine` via the existing `MediaService`. Private content is never bound to `<img src>` — it is fetched as a blob through `HttpClient` (the JWT interceptor supplies the token) and exposed as an object URL that the component revokes on destroy.

**Tech Stack:** Angular 20 (standalone, signals, `inject()`, OnPush), Tailwind 4, lucide-angular, Karma/Jasmine + `provideHttpClientTesting`.

## Global Constraints

- Angular 20 defaults to standalone: do **NOT** write `standalone: true` in decorators.
- Use `inject()`, never constructor injection. Use `signal()` / `computed()` for state.
- Use `ChangeDetectionStrategy.OnPush`.
- Use `input()` / `output()` functions, not decorators.
- No `any`. Strict types; templates are strictly typed (`number | undefined` will not bind to a `number` input).
- Reuse the existing `MediaService` at `src/app/services/media.service.ts`. Do **NOT** add a second HTTP path for media.
- Every object URL created with `URL.createObjectURL` MUST be released with `URL.revokeObjectURL`.
- Tests use `provideZonelessChangeDetection()`, `provideHttpClient()`, `provideHttpClientTesting()` — copy the setup shape from `src/app/services/media.service.spec.ts`.
- **TDD is mandatory:** write the failing test first, run it and see it fail for the right reason, then implement. Every behavioural rule gets a test of the rule AND a test of its opposite.
- Existing suite must stay green: `npx ng test --watch=false --browsers=ChromeHeadless` (36 passing before this plan). Build must pass: `npx ng build`.
- UI copy is French, matching the rest of the app.

### Existing interfaces you will use

`src/app/models/entities.ts`:

```ts
export type MediaCategory =
  | 'DIPLOMA' | 'CERTIFICATE' | 'BULLETIN' | 'PRESENTATION_VIDEO'
  | 'SCHOOL_LOGO' | 'SCHOOL_COVER' | 'COURSE_PHOTO' | 'PROGRAM_PHOTO' | 'USER_PHOTO';

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface Media {
  id: number;
  category: MediaCategory;
  visibility: 'PUBLIC' | 'PRIVATE';
  verificationStatus: VerificationStatus | null;
  rejectionReason: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  publicUrl: string | null;
}
```

`src/app/services/media.service.ts` (already implemented and tested):

```ts
resolveUrl(media?: Media | null): string | null
upload(file: File, category: MediaCategory, idempotencyKey?: string): Observable<Media>
blobUrl(mediaId: number): Observable<string>   // caller MUST revokeObjectURL
mine(): Observable<Media[]>
delete(mediaId: number): Observable<void>
```

---

### Task 1: UserDocumentsComponent — list and open own documents

**Files:**
- Create: `src/app/features/student/user-documents/user-documents.component.ts`
- Create: `src/app/features/student/user-documents/user-documents.component.html`
- Create: `src/app/features/student/user-documents/user-documents.component.spec.ts`

**Interfaces:**
- Consumes: `MediaService.mine()`, `MediaService.blobUrl()`, `Media`, `MediaCategory`, `VerificationStatus`.
- Produces (later tasks rely on these exact names):
  - `documents: Signal<Media[]>` — personal documents only
  - `loading: Signal<boolean>`, `error: Signal<string>`
  - `reload(): void` — re-fetches from `mine()`
  - `open(media: Media): void` — fetches blob, opens object URL
  - `statusLabel(status: VerificationStatus | null): string`
  - `categoryLabel(category: MediaCategory): string`

**Only these four categories are personal documents** — the component must filter out everything else returned by `mine()` (a `USER_PHOTO` is public and must not appear in the document list):

```ts
const PERSONAL_DOCUMENT_CATEGORIES: MediaCategory[] =
  ['DIPLOMA', 'CERTIFICATE', 'BULLETIN', 'PRESENTATION_VIDEO'];
```

Labels (exact strings):

```ts
// categoryLabel
DIPLOMA -> 'Diplôme'
CERTIFICATE -> 'Certificat'
BULLETIN -> 'Bulletin'
PRESENTATION_VIDEO -> 'Vidéo de présentation'

// statusLabel
'PENDING'  -> 'En attente'
'VERIFIED' -> 'Vérifié'
'REJECTED' -> 'Rejeté'
null       -> ''
```

- [ ] **Step 1: Write the failing spec**

Create `user-documents.component.spec.ts`. Copy the TestBed setup shape from `src/app/services/media.service.spec.ts` (same providers). Include a `media()` factory like that file's. Write these tests:

```
1. 'lists only personal documents returned by mine()'
   - flush mine() with [DIPLOMA, CERTIFICATE, USER_PHOTO]
   - expect component.documents().length === 2
   - expect documents() to contain no USER_PHOTO   <-- the opposite

2. 'shows an empty list when the user has no documents'
   - flush mine() with []
   - expect component.documents()).toEqual([])

3. 'exposes the French label for each verification status'
   - statusLabel('PENDING') === 'En attente'
   - statusLabel('VERIFIED') === 'Vérifié'
   - statusLabel('REJECTED') === 'Rejeté'
   - statusLabel(null) === ''                       <-- the opposite

4. 'open() fetches the media as a blob and produces an object URL'
   - call component.open(media({id: 7}))
   - expect a GET to `${environment.apiUrl}/media/7` with responseType 'blob'
   - flush a Blob

5. 'revokes every object URL it created on destroy'      <-- the leak guard
   - spyOn(URL, 'revokeObjectURL')
   - open() two different media, flush blobs
   - fixture.destroy()
   - expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)

6. 'sets an error message when loading documents fails'
   - flush mine() with a 500
   - expect(component.error()).toBeTruthy()
   - expect(component.loading()).toBeFalse()          <-- loading must clear
```

- [ ] **Step 2: Run the spec, confirm it fails**

Run: `npx ng test --watch=false --browsers=ChromeHeadless`
Expected: FAIL — the component does not exist yet.

- [ ] **Step 3: Implement the component**

`user-documents.component.ts` requirements:
- `@Component({ selector: 'app-user-documents', imports: [CommonModule, LucideAngularModule], templateUrl: './user-documents.component.html', changeDetection: ChangeDetectionStrategy.OnPush })`
- `private mediaService = inject(MediaService);`
- `private allMedia = signal<Media[]>([]);`
- `documents = computed(() => this.allMedia().filter(m => PERSONAL_DOCUMENT_CATEGORIES.includes(m.category)));`
- `loading = signal(false); error = signal('');`
- `ngOnInit()` calls `reload()`.
- `reload()` sets `loading(true)`, `error('')`, subscribes to `mediaService.mine()`, sets `allMedia` on next, sets a French error string on error, clears `loading` in **both** paths.
- `open(media: Media)` subscribes to `mediaService.blobUrl(media.id)`; on next, push the URL onto a private `objectUrls: string[]` and open it (`window.open(url, '_blank')`); on error set `error`.
- `ngOnDestroy()` iterates `objectUrls` calling `URL.revokeObjectURL(u)` and empties the array.
- `statusLabel` and `categoryLabel` per the tables above.

`user-documents.component.html` requirements:
- Section heading `Mes documents`.
- `@if (loading())` spinner/text; `@if (error())` error block showing `error()`.
- `@for (doc of documents(); track doc.id)` a row with: `categoryLabel(doc.category)`, `doc.originalFilename`, a status badge with `statusLabel(doc.verificationStatus)`, and an "Ouvrir" button calling `open(doc)`.
- Show `doc.rejectionReason` **only** when `doc.verificationStatus === 'REJECTED'`.
- `@empty` (or an `@if` on length) → `Aucun document`.
- Tailwind classes consistent with the profile page (`bg-white rounded-xl shadow-sm border border-gray-200 p-6`).

- [ ] **Step 4: Run the spec, confirm it passes**

Run: `npx ng test --watch=false --browsers=ChromeHeadless`
Expected: PASS, and the pre-existing 36 tests still pass.

- [ ] **Step 5: Verify the build**

Run: `npx ng build`
Expected: `Application bundle generation complete`, no ERROR lines. (A pre-existing bundle-budget WARNING at ~514 kB is expected and is not a failure.)

- [ ] **Step 6: Commit**

```bash
git add src/app/features/student/user-documents/
git commit -m "feat(profile): list and open own private documents"
```

---

### Task 2: Upload and delete documents, wired into the profile page

**Files:**
- Modify: `src/app/features/student/user-documents/user-documents.component.ts`
- Modify: `src/app/features/student/user-documents/user-documents.component.html`
- Modify: `src/app/features/student/user-documents/user-documents.component.spec.ts`
- Modify: `src/app/features/student/profile-page/profile-page.component.ts`
- Modify: `src/app/features/student/profile-page/profile-page.component.html`

**Interfaces:**
- Consumes everything Task 1 produced, plus `MediaService.upload()` and `MediaService.delete()`.
- Produces:
  - `selectedCategory: WritableSignal<MediaCategory>` (default `'DIPLOMA'`)
  - `uploading: Signal<boolean>`
  - `onFileSelected(event: Event): void`
  - `remove(media: Media): void`

**Upload rules (mirror the backend so the user gets a fast, clear error):**
- Max size 10485760 bytes (10 MB). Larger → set `error()` to a French message, do **not** call the API.
- Allowed extensions: `pdf`, `jpg`, `jpeg`, `png`, `webp`, `mp4`, `webm`, `mov`. Anything else → set `error()`, do **not** call the API.
- Send an idempotency key so a retried upload does not duplicate: `crypto.randomUUID()`.
- After a successful upload, call `reload()` so the list reflects server truth (including the `PENDING` status the server assigns).

- [ ] **Step 1: Write the failing tests**

Append to `user-documents.component.spec.ts`:

```
1. 'uploads the selected file with the chosen category and an idempotency key'
   - set component.selectedCategory.set('CERTIFICATE')
   - call onFileSelected with a fake event carrying a 1 KB File named 'a.pdf'
   - expect POST to `${environment.apiUrl}/media`
   - expect params category === 'CERTIFICATE'
   - expect header 'Idempotency-Key' to be truthy
   - flush a Media; then expect a follow-up GET to `/media/mine` (reload)

2. 'rejects a file above the size limit without calling the API'   <-- opposite
   - file of 10485761 bytes
   - expect(component.error()).toBeTruthy()
   - httpMock.expectNone(`${environment.apiUrl}/media`)

3. 'rejects a disallowed extension without calling the API'        <-- opposite
   - file named 'evil.exe'
   - expect(component.error()).toBeTruthy()
   - httpMock.expectNone(`${environment.apiUrl}/media`)

4. 'clears uploading and sets an error when the upload fails'
   - valid file, flush POST with 500
   - expect(component.error()).toBeTruthy()
   - expect(component.uploading()).toBeFalse()

5. 'deletes a document and reloads the list'
   - call component.remove(media({id: 5}))
   - expect DELETE to `${environment.apiUrl}/media/5`
   - flush; expect a follow-up GET to `/media/mine`
```

Note: build the file event as
`{ target: { files: [file] } } as unknown as Event`, and make the oversized
file with `new File([new ArrayBuffer(10485761)], 'big.pdf', { type: 'application/pdf' })`.

- [ ] **Step 2: Run the tests, confirm they fail**

Run: `npx ng test --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `onFileSelected` / `remove` do not exist.

- [ ] **Step 3: Implement upload and delete**

In the component:
- `selectedCategory = signal<MediaCategory>('DIPLOMA');`
- `uploading = signal(false);`
- `readonly uploadableCategories: MediaCategory[] = ['DIPLOMA', 'CERTIFICATE', 'BULLETIN', 'PRESENTATION_VIDEO'];`
- Private constants `MAX_UPLOAD_BYTES = 10485760` and
  `ALLOWED_EXTENSIONS = ['pdf','jpg','jpeg','png','webp','mp4','webm','mov']`.
- `onFileSelected(event)`: read `(event.target as HTMLInputElement).files?.[0]`; return if none. Validate size then extension (lowercased, after the last `.`), setting `error()` and returning early on failure. Otherwise clear `error()`, set `uploading(true)`, call `mediaService.upload(file, this.selectedCategory(), crypto.randomUUID())`, and on success clear `uploading` and call `reload()`; on error set `error()` and clear `uploading`.
- `remove(media)`: call `mediaService.delete(media.id)`, on success `reload()`, on error set `error()`.

In the template:
- A `<select>` bound to `selectedCategory` listing `uploadableCategories` with `categoryLabel()` as the option text.
- An `<input type="file" (change)="onFileSelected($event)">`, disabled while `uploading()`.
- A "Supprimer" button per row calling `remove(doc)`.

- [ ] **Step 4: Run the tests, confirm they pass**

Run: `npx ng test --watch=false --browsers=ChromeHeadless`
Expected: PASS (all tests, including Task 1's and the pre-existing 36).

- [ ] **Step 5: Embed in the profile page**

In `profile-page.component.ts`: add `UserDocumentsComponent` to the `imports` array of the `@Component` decorator and import it.

In `profile-page.component.html`: render `<app-user-documents />` inside the same `@if (profile().role?.name === 'STUDENT')` region that wraps the wishlist card, as a sibling card directly above the wishlist block.

- [ ] **Step 6: Verify build and full suite**

Run: `npx ng build`
Expected: `Application bundle generation complete`, no ERROR lines.

Run: `npx ng test --watch=false --browsers=ChromeHeadless`
Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/app/features/student/user-documents/ src/app/features/student/profile-page/
git commit -m "feat(profile): upload and delete own private documents"
```
