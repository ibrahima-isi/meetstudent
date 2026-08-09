# Media Access Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make personal documents (diplomas, certificates, bulletins, presentation videos) private — readable only by their owner and admins — with a role-gated upload flow, an admin moderation workflow, and idempotent uploads, while keeping school media and profile photos public.

**Architecture:** Introduce a `Media` JPA entity (one row per stored file) that carries ownership, category-derived visibility, and a verification status. Uploads return a media id, never a disk path. Public files stay under the statically-served `uploads/` dir; private files move to a separate `storage/private/` dir that is never mapped statically and is reachable only through an authorization-checked `GET /api/v1/media/{id}` endpoint. Low-level file I/O is isolated in `MediaStorageService`; orchestration, authorization, idempotency, and moderation live in `MediaService`.

**Tech Stack:** Spring Boot 3.5 (Java 21), Spring Data JPA, Spring Security (JWT, `@EnableMethodSecurity`), Flyway (Postgres), H2 for tests, JUnit 5 + Mockito + MockMvc.

## Global Constraints

- Java 21; Spring Boot 3.5.13 (parent). Do not add new third-party dependencies.
- Entities extend `com.bowe.meetstudent.entities.AbstractEntity` (Integer `id` via `GenerationType.IDENTITY`, plus `createdAt`/`modifiedAt`/`createdBy`/`modifiedBy` audit columns). Do NOT use `bigint`/`Long` ids — the codebase uses `Integer`.
- Array columns: never `@Column(columnDefinition = "text[]")`. Use `@org.hibernate.annotations.JdbcTypeCode(java.sql.Types.ARRAY)` on `List<String>` (H2/Postgres compatibility).
- Constructor injection via Lombok `@RequiredArgsConstructor`. Controllers return `ResponseEntity<T>` or `Page<T>`. Services hold `@Transactional`, never controllers.
- Tests: unit `*Test.java` (surefire, Mockito), integration `*IntegrationTests.java` (failsafe, `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`). Integration auth uses `TestDataUtil.mockUser(id, role)` / `mockUser(role)` — never `jwt().authorities(...)`. Integration paths use the full `/api/v1/...` prefix.
- Tests run on H2 with `ddl-auto: create-drop` and `flyway.enabled: false` (profile `test`, `application-test.yml`). The `media` table is therefore auto-generated from the entity in tests; the Flyway migrations (Task 2) are exercised only against real Postgres at deploy time.
- Every business rule is tested in BOTH directions (allowed action AND its forbidden opposite).
- Commit after each task. Branch: `feat/media-access-control` (already checked out).

---

## File Structure

**New (main):**
- `entities/enums/MediaVisibility.java` — `PUBLIC` / `PRIVATE`.
- `entities/enums/VerificationStatus.java` — `PENDING` / `VERIFIED` / `REJECTED`.
- `entities/enums/MediaCategory.java` — category enum carrying visibility + moderated flag + allowed roles.
- `entities/Media.java` — the media row.
- `repositories/MediaRepository.java` — Spring Data queries.
- `services/MediaStorageService.java` — low-level file I/O (atomic write, load, delete, path resolution, anti-traversal, public/private base dirs).
- `dto/MediaDTO.java` — response DTO.
- `dto/MediaVerificationRequest.java` — moderation request body.
- `mappers/implementations/MediaMapper.java` — `Media` → `MediaDTO`.
- `config/MediaMigrationRunner.java` — startup component that moves pre-existing private files out of `uploads/` into `storage/private/`.

**Modified (main):**
- `services/MediaService.java` — orchestration: validation (reused), upload+idempotency, download authorization, moderation, delete; remove `entityType`-based API when the controller stops using it.
- `controllers/MediaController.java` — new endpoints (upload/download/mine/verification/queue/delete).
- `config/WebConfig.java` — statically serve only the public subdir.
- `security/WebSecurityConfig.java` — media endpoint rules.
- `services/UserService.java` — stop managing media strings in `patch`.
- `entities/UserEntity.java` — remove `diplomas`/`certificates`/`presentationVideoUrl` string fields.
- `dto/UpdateProfileRequest.java` — drop media fields.
- `mappers/implementations/UserMapper.java` — populate media lists from `MediaService`.
- `dto/UserDTO.java` — media fields become `List<MediaDTO>` / `MediaDTO`.
- `src/main/resources/application.yml` (+ `-docker`, `-prod`, `-test`) — add `file.private-dir`.

**New (migrations, Postgres only):**
- `db/migration/V14__create_media_table.sql`
- `db/migration/V15__migrate_existing_media.sql`

**New (tests):**
- `unit/services/MediaStorageServiceTest.java`
- `unit/services/MediaServiceTest.java` (rewrite existing)
- `unit/mappers/MediaMapperTest.java`
- `unit/config/MediaMigrationRunnerTest.java`
- `integration/controllers/MediaControllerIntegrationTests.java` (rewrite existing)

---

## Task 1: Enums, Media entity, and repository

**Files:**
- Create: `src/main/java/com/bowe/meetstudent/entities/enums/MediaVisibility.java`
- Create: `src/main/java/com/bowe/meetstudent/entities/enums/VerificationStatus.java`
- Create: `src/main/java/com/bowe/meetstudent/entities/enums/MediaCategory.java`
- Create: `src/main/java/com/bowe/meetstudent/entities/Media.java`
- Create: `src/main/java/com/bowe/meetstudent/repositories/MediaRepository.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/entities/MediaCategoryTest.java`

**Interfaces:**
- Produces:
  - `enum MediaVisibility { PUBLIC, PRIVATE }`
  - `enum VerificationStatus { PENDING, VERIFIED, REJECTED }`
  - `enum MediaCategory` with `MediaVisibility getVisibility()`, `boolean isModerated()`, `Set<String> getAllowedUploadRoles()`, `boolean isPersonalDocument()`.
  - `class Media extends AbstractEntity` with fields `storageKey:String, originalFilename:String, contentType:String, sizeBytes:Long, category:MediaCategory, visibility:MediaVisibility, ownerId:Integer, verificationStatus:VerificationStatus, rejectionReason:String, idempotencyKey:String` (all with Lombok getters/setters, `@SuperBuilder`).
  - `interface MediaRepository extends JpaRepository<Media, Integer>` with:
    - `Optional<Media> findByOwnerIdAndIdempotencyKey(Integer ownerId, String idempotencyKey)`
    - `List<Media> findByOwnerId(Integer ownerId)`
    - `List<Media> findByOwnerIdAndCategory(Integer ownerId, MediaCategory category)`
    - `Page<Media> findByVerificationStatus(VerificationStatus status, Pageable pageable)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bowe/meetstudent/unit/entities/MediaCategoryTest.java`:
```java
package com.bowe.meetstudent.unit.entities;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaCategoryTest {

    @Test
    void personalDocumentsArePrivateModeratedAndUploadableByStudentExpertAdmin() {
        for (MediaCategory c : new MediaCategory[]{
                MediaCategory.DIPLOMA, MediaCategory.CERTIFICATE,
                MediaCategory.BULLETIN, MediaCategory.PRESENTATION_VIDEO}) {
            assertEquals(MediaVisibility.PRIVATE, c.getVisibility(), c.name());
            assertTrue(c.isModerated(), c.name());
            assertTrue(c.isPersonalDocument(), c.name());
            assertEquals(
                    java.util.Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"),
                    c.getAllowedUploadRoles(), c.name());
        }
    }

    @Test
    void schoolMediaIsPublicAndAdminOnly() {
        for (MediaCategory c : new MediaCategory[]{MediaCategory.SCHOOL_LOGO, MediaCategory.SCHOOL_COVER}) {
            assertEquals(MediaVisibility.PUBLIC, c.getVisibility(), c.name());
            assertFalse(c.isModerated(), c.name());
            assertFalse(c.isPersonalDocument(), c.name());
            assertEquals(java.util.Set.of("ROLE_ADMIN"), c.getAllowedUploadRoles(), c.name());
        }
    }

    @Test
    void userPhotoIsPublicAndUploadableByAnyAuthenticatedRole() {
        assertEquals(MediaVisibility.PUBLIC, MediaCategory.USER_PHOTO.getVisibility());
        assertFalse(MediaCategory.USER_PHOTO.isModerated());
        assertEquals(
                java.util.Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"),
                MediaCategory.USER_PHOTO.getAllowedUploadRoles());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaCategoryTest`
Expected: FAIL — compilation error, `MediaCategory` does not exist.

- [ ] **Step 3: Write the enums**

`src/main/java/com/bowe/meetstudent/entities/enums/MediaVisibility.java`:
```java
package com.bowe.meetstudent.entities.enums;

public enum MediaVisibility {
    PUBLIC,
    PRIVATE
}
```

`src/main/java/com/bowe/meetstudent/entities/enums/VerificationStatus.java`:
```java
package com.bowe.meetstudent.entities.enums;

public enum VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
```

`src/main/java/com/bowe/meetstudent/entities/enums/MediaCategory.java`:
```java
package com.bowe.meetstudent.entities.enums;

import java.util.Set;

public enum MediaCategory {
    DIPLOMA(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    CERTIFICATE(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    BULLETIN(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    PRESENTATION_VIDEO(MediaVisibility.PRIVATE, true, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN")),
    SCHOOL_LOGO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    SCHOOL_COVER(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    USER_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_STUDENT", "ROLE_EXPERT", "ROLE_ADMIN"));

    private final MediaVisibility visibility;
    private final boolean moderated;
    private final Set<String> allowedUploadRoles;

    MediaCategory(MediaVisibility visibility, boolean moderated, Set<String> allowedUploadRoles) {
        this.visibility = visibility;
        this.moderated = moderated;
        this.allowedUploadRoles = allowedUploadRoles;
    }

    public MediaVisibility getVisibility() {
        return visibility;
    }

    public boolean isModerated() {
        return moderated;
    }

    public Set<String> getAllowedUploadRoles() {
        return allowedUploadRoles;
    }

    public boolean isPersonalDocument() {
        return moderated && visibility == MediaVisibility.PRIVATE;
    }
}
```

- [ ] **Step 4: Write the entity and repository**

`src/main/java/com/bowe/meetstudent/entities/Media.java`:
```java
package com.bowe.meetstudent.entities;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "media",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_media_owner_idempotency",
                columnNames = {"owner_id", "idempotency_key"}))
public class Media extends AbstractEntity {

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MediaCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private MediaVisibility visibility;

    @Column(name = "owner_id")
    private Integer ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 16)
    private VerificationStatus verificationStatus;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;
}
```

`src/main/java/com/bowe/meetstudent/repositories/MediaRepository.java`:
```java
package com.bowe.meetstudent.repositories;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Integer> {

    Optional<Media> findByOwnerIdAndIdempotencyKey(Integer ownerId, String idempotencyKey);

    List<Media> findByOwnerId(Integer ownerId);

    List<Media> findByOwnerIdAndCategory(Integer ownerId, MediaCategory category);

    Page<Media> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaCategoryTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/entities/enums src/main/java/com/bowe/meetstudent/entities/Media.java src/main/java/com/bowe/meetstudent/repositories/MediaRepository.java src/test/java/com/bowe/meetstudent/unit/entities/MediaCategoryTest.java
git commit -m "feat(media): add Media entity, category/visibility/status enums, repository"
```

---

## Task 2: Flyway migrations (Postgres)

**Files:**
- Create: `src/main/resources/db/migration/V14__create_media_table.sql`
- Create: `src/main/resources/db/migration/V15__migrate_existing_media.sql`

**Interfaces:**
- Consumes: `Media` entity column names from Task 1 (`media` table schema must match so prod `ddl-auto: validate` boots).
- Produces: a populated `media` table and the pre-existing user/school media rows.

> **Note:** The test suite (H2, `flyway.enabled: false`, `ddl-auto: create-drop`) does NOT run these files, so there is no automated test. Correctness is validated by Task 12's manual boot against Postgres. Match the entity column definitions from Task 1 exactly.

- [ ] **Step 1: Write V14 (schema)**

`src/main/resources/db/migration/V14__create_media_table.sql`:
```sql
CREATE TABLE IF NOT EXISTS media (
    id integer PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
    storage_key varchar(512) NOT NULL,
    original_filename varchar(255),
    content_type varchar(128),
    size_bytes bigint,
    category varchar(32) NOT NULL,
    visibility varchar(16) NOT NULL,
    owner_id integer REFERENCES users(id),
    verification_status varchar(16),
    rejection_reason varchar(500),
    idempotency_key varchar(128),
    created_at timestamp(6) without time zone,
    modified_at timestamp(6) without time zone,
    created_by integer,
    modified_by integer,
    CONSTRAINT uq_media_owner_idempotency UNIQUE (owner_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_media_owner ON media (owner_id);
CREATE INDEX IF NOT EXISTS idx_media_status ON media (verification_status);
```

- [ ] **Step 2: Write V15 (data migration)**

`src/main/resources/db/migration/V15__migrate_existing_media.sql`:
```sql
-- Migrate existing private personal documents (diploma/certificate string arrays)
-- into the media table. Physical files are relocated by MediaMigrationRunner at startup;
-- storage_key here keeps the legacy relative path until the runner rewrites it.

-- Diplomas: users.diplomas is a text[] of relative paths (e.g. 'users/uuid.pdf')
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT d AS storage_key,
       d AS original_filename,
       NULL AS content_type,
       'DIPLOMA' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u, unnest(u.diplomas) AS d
WHERE u.diplomas IS NOT NULL;

-- Certificates
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT c AS storage_key,
       c AS original_filename,
       NULL AS content_type,
       'CERTIFICATE' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u, unnest(u.certificates) AS c
WHERE u.certificates IS NOT NULL;

-- Presentation videos (private, single value per user)
INSERT INTO media (storage_key, original_filename, content_type, category, visibility, owner_id, verification_status, created_at, modified_at)
SELECT u.presentation_video_url AS storage_key,
       u.presentation_video_url AS original_filename,
       NULL AS content_type,
       'PRESENTATION_VIDEO' AS category,
       'PRIVATE' AS visibility,
       u.id AS owner_id,
       'PENDING' AS verification_status,
       now() AS created_at,
       now() AS modified_at
FROM users u
WHERE u.presentation_video_url IS NOT NULL AND u.presentation_video_url <> '';

-- Drop the legacy media columns now that data lives in the media table.
ALTER TABLE users DROP COLUMN IF EXISTS diplomas;
ALTER TABLE users DROP COLUMN IF EXISTS certificates;
ALTER TABLE users DROP COLUMN IF EXISTS presentation_video_url;
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V14__create_media_table.sql src/main/resources/db/migration/V15__migrate_existing_media.sql
git commit -m "feat(media): add Flyway migrations for media table and legacy data migration"
```

---

## Task 3: MediaStorageService (file I/O)

**Files:**
- Create: `src/main/java/com/bowe/meetstudent/services/MediaStorageService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/MediaStorageServiceTest.java`

**Interfaces:**
- Consumes: `MediaVisibility` (Task 1).
- Produces:
  - `String store(byte[] content, String extension, MediaVisibility visibility) throws IOException` — writes atomically (`.tmp` then move), returns a `storageKey` of the form `public/<uuid>.<ext>` or `private/<uuid>.<ext>`.
  - `Resource loadAsResource(String storageKey) throws IOException` — returns a readable Spring `Resource` for the stored file; throws if outside the base dir.
  - `boolean delete(String storageKey) throws IOException` — deletes, returns true if a file was removed.
  - Reads config `file.upload-dir` (default `uploads`) and `file.private-dir` (default `storage/private`).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bowe/meetstudent/unit/services/MediaStorageServiceTest.java`:
```java
package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MediaStorageServiceTest {

    @TempDir
    Path tempDir;

    private MediaStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new MediaStorageService();
        ReflectionTestUtils.setField(storage, "publicDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(storage, "privateDir", tempDir.resolve("storage/private").toString());
    }

    @Test
    void storePrivateWritesUnderPrivateDirAndReturnsPrefixedKey() throws IOException {
        String key = storage.store("hello".getBytes(), "pdf", MediaVisibility.PRIVATE);

        assertTrue(key.startsWith("private/"), key);
        assertTrue(key.endsWith(".pdf"), key);
        Path physical = tempDir.resolve("storage/private").resolve(key.substring("private/".length()));
        assertTrue(Files.exists(physical));
        // must NOT be under the statically served public dir
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve(key)));
    }

    @Test
    void storePublicWritesUnderPublicDir() throws IOException {
        String key = storage.store("img".getBytes(), "png", MediaVisibility.PUBLIC);

        assertTrue(key.startsWith("public/"), key);
        Path physical = tempDir.resolve("uploads").resolve(key);
        assertTrue(Files.exists(physical));
    }

    @Test
    void loadAsResourceReturnsStoredContent() throws IOException {
        String key = storage.store("data".getBytes(), "pdf", MediaVisibility.PRIVATE);

        Resource resource = storage.loadAsResource(key);

        assertTrue(resource.exists());
        assertEquals("data", new String(resource.getInputStream().readAllBytes()));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        String key = storage.store("x".getBytes(), "pdf", MediaVisibility.PRIVATE);

        assertTrue(storage.delete(key));
        assertThrows(IOException.class, () -> storage.loadAsResource(key));
    }

    @Test
    void loadRejectsPathTraversalKey() {
        assertThrows(IOException.class, () -> storage.loadAsResource("private/../../etc/passwd"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaStorageServiceTest`
Expected: FAIL — `MediaStorageService` does not exist.

- [ ] **Step 3: Implement MediaStorageService**

`src/main/java/com/bowe/meetstudent/services/MediaStorageService.java`:
```java
package com.bowe.meetstudent.services;

import com.bowe.meetstudent.entities.enums.MediaVisibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class MediaStorageService {

    private static final String PUBLIC_PREFIX = "public/";
    private static final String PRIVATE_PREFIX = "private/";

    @Value("${file.upload-dir:uploads}")
    private String publicDir;

    @Value("${file.private-dir:storage/private}")
    private String privateDir;

    public String store(byte[] content, String extension, MediaVisibility visibility) throws IOException {
        String fileName = UUID.randomUUID() + "." + extension;
        boolean isPublic = visibility == MediaVisibility.PUBLIC;
        String key = (isPublic ? PUBLIC_PREFIX : PRIVATE_PREFIX) + fileName;

        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        Path tmp = target.resolveSibling(fileName + ".tmp");
        Files.write(tmp, content);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        return key;
    }

    public Resource loadAsResource(String storageKey) throws IOException {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
            throw new IOException("Media not found");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IOException("Media not readable");
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw new IOException("Invalid media path", e);
        }
    }

    public boolean delete(String storageKey) throws IOException {
        return Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) throws IOException {
        Path base;
        String relative;
        if (storageKey != null && storageKey.startsWith(PRIVATE_PREFIX)) {
            base = privateBase();
            relative = storageKey.substring(PRIVATE_PREFIX.length());
        } else if (storageKey != null && storageKey.startsWith(PUBLIC_PREFIX)) {
            base = publicBase();
            relative = storageKey.substring(PUBLIC_PREFIX.length());
        } else {
            throw new IOException("Invalid storage key");
        }
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("Invalid media path");
        }
        return resolved;
    }

    private Path publicBase() {
        return Paths.get(publicDir).toAbsolutePath().normalize();
    }

    private Path privateBase() {
        return Paths.get(privateDir).toAbsolutePath().normalize();
    }
}
```

Note: the physical layout is `${publicDir}/public/<uuid>.ext` and `${privateDir}/private/<uuid>.ext` — the prefix is part of the stored key so `resolve` can pick the base dir. This keeps public files reachable at the static URL `/uploads/public/<uuid>.ext` (Task 9 restricts `/uploads/**` to that subtree only in effect, since private files live elsewhere).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaStorageServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/services/MediaStorageService.java src/test/java/com/bowe/meetstudent/unit/services/MediaStorageServiceTest.java
git commit -m "feat(media): add MediaStorageService with atomic writes and public/private dirs"
```

---

## Task 4: MediaService — validation + idempotent upload

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/services/MediaService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java` (rewrite)

**Interfaces:**
- Consumes: `MediaStorageService.store(...)` (Task 3), `MediaRepository` (Task 1), `MediaCategory`/`MediaVisibility`/`VerificationStatus` (Task 1), `UserPrincipal` (existing).
- Produces:
  - `Media upload(MultipartFile file, MediaCategory category, UserPrincipal principal, String idempotencyKey) throws IOException` — asserts role, validates file, dedupes by `(ownerId, idempotencyKey)`, stores, persists a `Media` (`verificationStatus = PENDING` iff `category.isModerated()`, else null; `ownerId` = principal id for personal/photo categories, null for school categories).
  - `void assertCanUpload(UserPrincipal principal, MediaCategory category)` — throws `AccessDeniedException` if the principal has none of `category.getAllowedUploadRoles()`.
  - Keep existing private helpers `validateFile`, `validateFileContent`, `normalizeContentType` (reused; make `validateFile` return the extension as today).

Retain the existing constructor field `MediaStorageService` (add it) and `MediaRepository`. Convert `MediaService` to `@RequiredArgsConstructor` with `private final MediaStorageService storageService;` and `private final MediaRepository mediaRepository;`. Remove the old `saveMedia(MultipartFile, String entityType)` / `isAllowedEntityType` / `ALLOWED_ENTITY_TYPES` / `getUploadBasePath` / `ensurePathInsideUploadDir` / URL-delete helpers ONLY in Task 8/Task 10 where their callers are removed — for THIS task, keep them so the module still compiles; add the new methods alongside.

- [ ] **Step 1: Write the failing test (rewrite MediaServiceTest)**

`src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java`:
```java
package com.bowe.meetstudent.unit.services;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.repositories.MediaRepository;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MediaService;
import com.bowe.meetstudent.services.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Mock MediaStorageService storageService;
    @Mock MediaRepository mediaRepository;
    @InjectMocks MediaService mediaService;

    private UserPrincipal principal(Integer id, String role) {
        return UserPrincipal.builder().id(id).username("u@x.com")
                .authorities(List.of(new SimpleGrantedAuthority(role))).build();
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG);
    }

    private void wireStorageAndSave() throws IOException {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 10_485_760L);
        when(storageService.store(any(), any(), any())).thenReturn("private/uuid.jpg");
        when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void uploadPersonalDocumentAsStudentPersistsPendingMediaOwnedByPrincipal() throws IOException {
        wireStorageAndSave();
        when(mediaRepository.findByOwnerIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        Media saved = mediaService.upload(jpeg(), MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), "key-1");

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        Media persisted = captor.getValue();
        assertEquals(7, persisted.getOwnerId());
        assertEquals(MediaCategory.DIPLOMA, persisted.getCategory());
        assertEquals(MediaVisibility.PRIVATE, persisted.getVisibility());
        assertEquals(VerificationStatus.PENDING, persisted.getVerificationStatus());
        assertEquals("key-1", persisted.getIdempotencyKey());
        assertEquals("photo.jpg", persisted.getOriginalFilename());
        assertNotNull(saved);
    }

    @Test
    void uploadSchoolLogoAsStudentIsForbidden() {
        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> mediaService.upload(jpeg(), MediaCategory.SCHOOL_LOGO, principal(7, "ROLE_STUDENT"), null));
        assertNotNull(ex);
        verifyNoInteractions(storageService);
    }

    @Test
    void uploadSchoolLogoAsAdminHasNoOwnerAndNoStatus() throws IOException {
        wireStorageAndSave();
        when(storageService.store(any(), any(), any())).thenReturn("public/uuid.png");

        Media saved = mediaService.upload(
                new MockMultipartFile("file", "logo.png",
                        "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0}),
                MediaCategory.SCHOOL_LOGO, principal(9, "ROLE_ADMIN"), null);

        assertNull(saved.getOwnerId());
        assertNull(saved.getVerificationStatus());
        assertEquals(MediaVisibility.PUBLIC, saved.getVisibility());
    }

    @Test
    void uploadWithExistingIdempotencyKeyReturnsExistingMediaWithoutStoring() throws IOException {
        Media existing = Media.builder().storageKey("private/old.jpg")
                .category(MediaCategory.DIPLOMA).visibility(MediaVisibility.PRIVATE)
                .ownerId(7).idempotencyKey("key-1").build();
        when(mediaRepository.findByOwnerIdAndIdempotencyKey(7, "key-1")).thenReturn(Optional.of(existing));

        Media result = mediaService.upload(jpeg(), MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), "key-1");

        assertSame(existing, result);
        verifyNoInteractions(storageService);
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void uploadRejectsContentNotMatchingDeclaredType() {
        ReflectionTestUtils.setField(mediaService, "maxUploadBytes", 10_485_760L);
        MockMultipartFile bad = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not a jpeg".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> mediaService.upload(bad, MediaCategory.DIPLOMA, principal(7, "ROLE_STUDENT"), null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: FAIL — `upload(...)` and the new constructor deps do not exist.

- [ ] **Step 3: Implement (add to MediaService)**

Refactor `MediaService` to inject the new collaborators and add the upload flow. Replace the class header and add fields/methods; keep the existing validation helpers (`validateFile`, `validateFileContent`, `startsWith`, `normalizeContentType`, and the `ALLOWED_MIME_TYPES_BY_EXTENSION`/`maxUploadBytes` fields). New top of class:

```java
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaStorageService storageService;
    private final MediaRepository mediaRepository;

    @Value("${file.max-upload-bytes:10485760}")
    private long maxUploadBytes;

    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES_BY_EXTENSION = Map.of(
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"),
            "webp", Set.of("image/webp"),
            "pdf", Set.of("application/pdf"),
            "mp4", Set.of("video/mp4"),
            "webm", Set.of("video/webm"),
            "mov", Set.of("video/quicktime")
    );

    @Transactional
    public Media upload(MultipartFile file, MediaCategory category,
                        UserPrincipal principal, String idempotencyKey) throws IOException {
        assertCanUpload(principal, category);

        Integer ownerId = category.isPersonalDocument() || category == MediaCategory.USER_PHOTO
                ? principal.getId()
                : null;

        if (idempotencyKey != null && !idempotencyKey.isBlank() && ownerId != null) {
            Optional<Media> existing = mediaRepository.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String extension = validateFile(file);
        byte[] content = file.getBytes();
        validateFileContent(extension, content);

        String storageKey = storageService.store(content, extension, category.getVisibility());

        Media media = Media.builder()
                .storageKey(storageKey)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .category(category)
                .visibility(category.getVisibility())
                .ownerId(ownerId)
                .verificationStatus(category.isModerated() ? VerificationStatus.PENDING : null)
                .idempotencyKey(idempotencyKey)
                .build();

        return mediaRepository.save(media);
    }

    public void assertCanUpload(UserPrincipal principal, MediaCategory category) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        boolean allowed = principal.getAuthorities().stream()
                .anyMatch(a -> category.getAllowedUploadRoles().contains(a.getAuthority()));
        if (!allowed) {
            throw new AccessDeniedException("You are not allowed to upload this media type.");
        }
    }
```

Add the imports: `com.bowe.meetstudent.entities.Media`, the three enums, `com.bowe.meetstudent.repositories.MediaRepository`, `com.bowe.meetstudent.security.UserPrincipal`, `lombok.RequiredArgsConstructor`, `org.springframework.security.access.AccessDeniedException`, `org.springframework.transaction.annotation.Transactional`, `java.io.IOException`, `java.util.Optional`. Keep the existing validation helper methods (`validateFile`, `validateFileContent`, `startsWith`, `normalizeContentType`) unchanged. Remove the now-unused `uploadDir`/`getUploadBasePath`/`ensurePathInsideUploadDir`/`saveMedia(entityType)`/`deleteMedia`/`deleteMediaByUrl`/`deleteOldMediaIfChanged`/`deleteRemovedMedia`/`isAllowedEntityType`/`validateEntityType`/`ALLOWED_ENTITY_TYPES` members ONLY after Task 10 (their callers still exist now). For this task, leave them in place if they still compile with the new constructor; if the removed `uploadDir` field is still referenced by them, keep it too. (The clean removal happens in Task 10.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/services/MediaService.java src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java
git commit -m "feat(media): add role-gated idempotent upload to MediaService"
```

---

## Task 5: MediaService — download authorization

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/services/MediaService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java` (add methods)

**Interfaces:**
- Consumes: `MediaRepository.findById`, `MediaStorageService.loadAsResource` (Tasks 1, 3).
- Produces:
  - `Media getAccessibleMedia(Integer mediaId, UserPrincipal principal)` — returns the `Media` if PUBLIC, or if PRIVATE and principal is the owner or has `ROLE_ADMIN`; throws `ResourceNotFoundException` if missing, `AccessDeniedException` if private and not permitted.
  - `Resource loadContent(Media media) throws IOException` — delegates to storage.
  - `boolean isAdmin(UserPrincipal principal)` (private helper).

- [ ] **Step 1: Write the failing test (append to MediaServiceTest)**

Add these methods and imports (`com.bowe.meetstudent.exceptions.ResourceNotFoundException`) to `MediaServiceTest`:
```java
    @Test
    void publicMediaIsAccessibleToAnonymous() {
        Media m = Media.builder().visibility(MediaVisibility.PUBLIC).category(MediaCategory.SCHOOL_LOGO).build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, null));
    }

    @Test
    void privateMediaIsAccessibleToOwner() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, principal(7, "ROLE_STUDENT")));
    }

    @Test
    void privateMediaIsAccessibleToAdmin() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertSame(m, mediaService.getAccessibleMedia(5, principal(99, "ROLE_ADMIN")));
    }

    @Test
    void privateMediaIsForbiddenToOtherUser() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> mediaService.getAccessibleMedia(5, principal(8, "ROLE_STUDENT")));
    }

    @Test
    void missingMediaThrowsNotFound() {
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.empty());

        assertThrows(com.bowe.meetstudent.exceptions.ResourceNotFoundException.class,
                () -> mediaService.getAccessibleMedia(5, principal(7, "ROLE_STUDENT")));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: FAIL — `getAccessibleMedia` not defined.

- [ ] **Step 3: Implement (add to MediaService)**

```java
    public Media getAccessibleMedia(Integer mediaId, UserPrincipal principal) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

        if (media.getVisibility() == MediaVisibility.PUBLIC) {
            return media;
        }
        boolean owner = principal != null && principal.getId() != null
                && principal.getId().equals(media.getOwnerId());
        if (owner || isAdmin(principal)) {
            return media;
        }
        throw new AccessDeniedException("You cannot access this document.");
    }

    public Resource loadContent(Media media) throws IOException {
        return storageService.loadAsResource(media.getStorageKey());
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal != null && principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
```

Add imports: `com.bowe.meetstudent.exceptions.ResourceNotFoundException`, `org.springframework.core.io.Resource`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: PASS (10 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/services/MediaService.java src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java
git commit -m "feat(media): add owner/admin download authorization to MediaService"
```

---

## Task 6: MediaService — moderation, listing, delete

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/services/MediaService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java` (add methods)

**Interfaces:**
- Consumes: `MediaRepository` (findById, findByVerificationStatus, findByOwnerId, save, delete), `MediaStorageService.delete` (Tasks 1, 3).
- Produces:
  - `Media setVerification(Integer mediaId, VerificationStatus status, String reason)` — loads media, sets status (and `rejectionReason` when `REJECTED`, cleared otherwise), saves. Throws `ResourceNotFoundException` if missing; `IllegalArgumentException` if status is `PENDING` (only VERIFIED/REJECTED are settable by an admin).
  - `Page<Media> findByStatus(VerificationStatus status, Pageable pageable)`.
  - `List<Media> findOwnedBy(Integer ownerId)`.
  - `void delete(Integer mediaId, UserPrincipal principal) throws IOException` — owner-or-admin (reuses the access check), deletes file then row.

- [ ] **Step 1: Write the failing test (append to MediaServiceTest)**

```java
    @Test
    void setVerificationToRejectedStoresReason() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7)
                .verificationStatus(VerificationStatus.PENDING).build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));
        org.mockito.Mockito.when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        Media result = mediaService.setVerification(5, VerificationStatus.REJECTED, "blurry scan");

        assertEquals(VerificationStatus.REJECTED, result.getVerificationStatus());
        assertEquals("blurry scan", result.getRejectionReason());
    }

    @Test
    void setVerificationToVerifiedClearsReason() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7)
                .verificationStatus(VerificationStatus.REJECTED).rejectionReason("old").build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));
        org.mockito.Mockito.when(mediaRepository.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));

        Media result = mediaService.setVerification(5, VerificationStatus.VERIFIED, null);

        assertEquals(VerificationStatus.VERIFIED, result.getVerificationStatus());
        assertNull(result.getRejectionReason());
    }

    @Test
    void setVerificationToPendingIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> mediaService.setVerification(5, VerificationStatus.PENDING, null));
    }

    @Test
    void deleteByOwnerRemovesFileAndRow() throws Exception {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).storageKey("private/x.pdf").build();
        ReflectionTestUtils.setField(m, "id", 5);
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        mediaService.delete(5, principal(7, "ROLE_STUDENT"));

        verify(storageService).delete("private/x.pdf");
        verify(mediaRepository).delete(m);
    }

    @Test
    void deleteByOtherUserIsForbidden() {
        Media m = Media.builder().visibility(MediaVisibility.PRIVATE)
                .category(MediaCategory.DIPLOMA).ownerId(7).storageKey("private/x.pdf").build();
        org.mockito.Mockito.when(mediaRepository.findById(5)).thenReturn(Optional.of(m));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> mediaService.delete(5, principal(8, "ROLE_STUDENT")));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: FAIL — `setVerification`/`delete`/`findByStatus`/`findOwnedBy` not defined.

- [ ] **Step 3: Implement (add to MediaService)**

```java
    @Transactional
    public Media setVerification(Integer mediaId, VerificationStatus status, String reason) {
        if (status == null || status == VerificationStatus.PENDING) {
            throw new IllegalArgumentException("Status must be VERIFIED or REJECTED.");
        }
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        media.setVerificationStatus(status);
        media.setRejectionReason(status == VerificationStatus.REJECTED ? reason : null);
        return mediaRepository.save(media);
    }

    public Page<Media> findByStatus(VerificationStatus status, Pageable pageable) {
        return mediaRepository.findByVerificationStatus(status, pageable);
    }

    public List<Media> findOwnedBy(Integer ownerId) {
        return mediaRepository.findByOwnerId(ownerId);
    }

    @Transactional
    public void delete(Integer mediaId, UserPrincipal principal) throws IOException {
        Media media = getAccessibleMedia(mediaId, principal);
        storageService.delete(media.getStorageKey());
        mediaRepository.delete(media);
    }
```

Add imports: `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`, `java.util.List`.

Note: `getAccessibleMedia` returns PUBLIC media to anyone, but `delete` is additionally gated at the HTTP layer (Task 9: `DELETE /media/**` requires authentication) and by the fact that only owners/admins reach personal media; a non-owner deleting a PUBLIC school logo is prevented by the ADMIN-only rule on that path in Task 9. For personal media the owner/admin check here is authoritative.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: PASS (15 tests total).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/services/MediaService.java src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java
git commit -m "feat(media): add moderation, owner listing, and delete to MediaService"
```

---

## Task 7: MediaDTO, MediaVerificationRequest, MediaMapper

**Files:**
- Create: `src/main/java/com/bowe/meetstudent/dto/MediaDTO.java`
- Create: `src/main/java/com/bowe/meetstudent/dto/MediaVerificationRequest.java`
- Create: `src/main/java/com/bowe/meetstudent/mappers/implementations/MediaMapper.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/mappers/MediaMapperTest.java`

**Interfaces:**
- Consumes: `Media` (Task 1), `Mapper<A,B>` interface (existing).
- Produces:
  - `class MediaDTO { Integer id; MediaCategory category; MediaVisibility visibility; VerificationStatus verificationStatus; String rejectionReason; String originalFilename; String contentType; Long sizeBytes; }` (Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`). NO `storageKey` (never exposed).
  - `class MediaVerificationRequest { @NotNull VerificationStatus status; @Size(max=500) String reason; }`.
  - `class MediaMapper implements Mapper<Media, MediaDTO>` — `toDTO` maps fields except `storageKey`; `toEntity` throws `UnsupportedOperationException` (media are created via upload, not mapped from DTO).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bowe/meetstudent/unit/mappers/MediaMapperTest.java`:
```java
package com.bowe.meetstudent.unit.mappers;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.mappers.implementations.MediaMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MediaMapperTest {

    private final MediaMapper mapper = new MediaMapper();

    @Test
    void toDtoCopiesSafeFieldsAndOmitsStorageKey() {
        Media media = Media.builder()
                .storageKey("private/secret.pdf")
                .originalFilename("diploma.pdf")
                .contentType("application/pdf")
                .sizeBytes(1234L)
                .category(MediaCategory.DIPLOMA)
                .visibility(MediaVisibility.PRIVATE)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(media, "id", 42);

        MediaDTO dto = mapper.toDTO(media);

        assertEquals(42, dto.getId());
        assertEquals("diploma.pdf", dto.getOriginalFilename());
        assertEquals(MediaCategory.DIPLOMA, dto.getCategory());
        assertEquals(MediaVisibility.PRIVATE, dto.getVisibility());
        assertEquals(VerificationStatus.PENDING, dto.getVerificationStatus());
        assertEquals(1234L, dto.getSizeBytes());
    }

    @Test
    void toEntityIsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> mapper.toEntity(new MediaDTO()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaMapperTest`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement DTOs and mapper**

`src/main/java/com/bowe/meetstudent/dto/MediaDTO.java`:
```java
package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDTO {
    private Integer id;
    private MediaCategory category;
    private MediaVisibility visibility;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
}
```

`src/main/java/com/bowe/meetstudent/dto/MediaVerificationRequest.java`:
```java
package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.enums.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaVerificationRequest {

    @NotNull(message = "status requis")
    private VerificationStatus status;

    @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères")
    private String reason;
}
```

`src/main/java/com/bowe/meetstudent/mappers/implementations/MediaMapper.java`:
```java
package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.mappers.Mapper;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper implements Mapper<Media, MediaDTO> {

    @Override
    public MediaDTO toDTO(Media media) {
        return MediaDTO.builder()
                .id(media.getId())
                .category(media.getCategory())
                .visibility(media.getVisibility())
                .verificationStatus(media.getVerificationStatus())
                .rejectionReason(media.getRejectionReason())
                .originalFilename(media.getOriginalFilename())
                .contentType(media.getContentType())
                .sizeBytes(media.getSizeBytes())
                .build();
    }

    @Override
    public Media toEntity(MediaDTO mediaDTO) {
        throw new UnsupportedOperationException("Media are created through upload, not mapped from a DTO");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaMapperTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/dto/MediaDTO.java src/main/java/com/bowe/meetstudent/dto/MediaVerificationRequest.java src/main/java/com/bowe/meetstudent/mappers/implementations/MediaMapper.java src/test/java/com/bowe/meetstudent/unit/mappers/MediaMapperTest.java
git commit -m "feat(media): add MediaDTO, verification request, and MediaMapper"
```

---

## Task 8: MediaController rewrite + security rules

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/controllers/MediaController.java`
- Modify: `src/main/java/com/bowe/meetstudent/config/WebConfig.java`
- Modify: `src/main/java/com/bowe/meetstudent/security/WebSecurityConfig.java`
- Modify: `src/main/resources/application.yml`, `application-docker.yml`, `application-prod.yml`, `application-test.yml`
- Test: `src/test/java/com/bowe/meetstudent/integration/controllers/MediaControllerIntegrationTests.java` (rewrite)

**Interfaces:**
- Consumes: `MediaService.upload/getAccessibleMedia/loadContent/setVerification/findByStatus/findOwnedBy/delete` (Tasks 4-6), `MediaMapper.toDTO` (Task 7), `MediaDTO`, `MediaVerificationRequest`, `UserPrincipal`.
- Produces HTTP endpoints:
  - `POST /api/v1/media?category={CATEGORY}` (multipart `file`, optional header `Idempotency-Key`) → 201 `MediaDTO` (200 on idempotent hit — for simplicity return 201 always; the same media is returned).
  - `GET /api/v1/media/{id}` → streams bytes (`Resource`) with `Content-Type` + `Content-Disposition`.
  - `GET /api/v1/media/mine` → `List<MediaDTO>`.
  - `GET /api/v1/media?status=PENDING` (paged) → `Page<MediaDTO>` (ADMIN).
  - `PATCH /api/v1/media/{id}/verification` → `MediaDTO` (ADMIN).
  - `DELETE /api/v1/media/{id}` → 204.

- [ ] **Step 1: Add `file.private-dir` config**

Add to `src/main/resources/application.yml` under the existing `file:` block:
```yaml
file:
  upload-dir: uploads
  private-dir: storage/private
  max-upload-bytes: 10485760
```
Add `file.private-dir: storage/private` (and keep `upload-dir`) to `application-docker.yml`, `application-prod.yml`. Add to `src/main/resources/application-test.yml`:
```yaml
file:
  upload-dir: target/test-uploads
  private-dir: target/test-private
  max-upload-bytes: 10485760
```

- [ ] **Step 2: Write the failing integration test (rewrite)**

`src/test/java/com/bowe/meetstudent/integration/controllers/MediaControllerIntegrationTests.java`:
```java
package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerIntegrationTests {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0};

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF);
    }

    private int uploadDiplomaAs(int userId) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(userId, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asInt();
    }

    @Test
    void studentCanUploadDiploma() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void anonymousCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "DIPLOMA"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotUploadSchoolLogo() throws Exception {
        mockMvc.perform(multipart("/api/v1/media")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .param("category", "SCHOOL_LOGO")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanDownloadOwnPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void otherUserCannotDownloadPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDownloadPrivateDocument() throws Exception {
        int id = uploadDiplomaAs(7);
        mockMvc.perform(get("/api/v1/media/" + id).with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameMedia() throws Exception {
        String first = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA").header("Idempotency-Key", "k-1")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(multipart("/api/v1/media").file(pdf())
                        .param("category", "DIPLOMA").header("Idempotency-Key", "k-1")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                objectMapper.readTree(first).get("id").asInt(),
                objectMapper.readTree(second).get("id").asInt());
    }

    @Test
    void adminCanVerifyDocumentAndNonAdminCannot() throws Exception {
        int id = uploadDiplomaAs(7);

        mockMvc.perform(patch("/api/v1/media/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mockMvc.perform(patch("/api/v1/media/" + id + "/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"reason\":\"x\"}")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mineReturnsOnlyCallersMedia() throws Exception {
        uploadDiplomaAs(7);
        uploadDiplomaAs(8);

        mockMvc.perform(get("/api/v1/media/mine").with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=MediaControllerIntegrationTests`
Expected: FAIL — new controller endpoints not present.

- [ ] **Step 4: Rewrite MediaController**

`src/main/java/com/bowe/meetstudent/controllers/MediaController.java`:
```java
package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.dto.MediaDTO;
import com.bowe.meetstudent.dto.MediaVerificationRequest;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.VerificationStatus;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.security.UserPrincipal;
import com.bowe.meetstudent.services.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
@Tag(name = "12. Media", description = "Upload, download, moderation of media and documents")
public class MediaController {

    private final MediaService mediaService;
    private final Mapper<Media, MediaDTO> mediaMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a media file", description = "Uploads a file for a category and returns its metadata (id, status). Send an Idempotency-Key header to make retries safe.")
    public ResponseEntity<MediaDTO> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("category") MediaCategory category,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) throws IOException {
        Media media = mediaService.upload(file, category, principal, idempotencyKey);
        return new ResponseEntity<>(mediaMapper.toDTO(media), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Download a media file", description = "Public media is served to anyone; private media only to its owner or an admin.")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) throws IOException {
        Media media = mediaService.getAccessibleMedia(id, principal);
        Resource resource = mediaService.loadContent(media);
        String contentType = media.getContentType() != null ? media.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (media.getOriginalFilename() != null ? media.getOriginalFilename() : "file") + "\"")
                .body(resource);
    }

    @GetMapping("/mine")
    @Operation(summary = "List my media", description = "Returns the authenticated user's own media with verification status.")
    public ResponseEntity<List<MediaDTO>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        List<MediaDTO> dtos = mediaService.findOwnedBy(principal.getId()).stream().map(mediaMapper::toDTO).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Moderation queue", description = "Lists media filtered by verification status (admin).")
    public Page<MediaDTO> queue(
            @RequestParam(defaultValue = "PENDING") VerificationStatus status,
            @ParameterObject Pageable pageable) {
        return mediaService.findByStatus(status, pageable).map(mediaMapper::toDTO);
    }

    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify or reject a document (admin)")
    public ResponseEntity<MediaDTO> verify(
            @PathVariable Integer id,
            @RequestBody @Validated MediaVerificationRequest request) {
        Media media = mediaService.setVerification(id, request.getStatus(), request.getReason());
        return ResponseEntity.ok(mediaMapper.toDTO(media));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a media file", description = "Owner or admin only.")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Integer id) throws IOException {
        mediaService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Restrict static serving to the public dir (WebConfig)**

`src/main/java/com/bowe/meetstudent/config/WebConfig.java`:
```java
package com.bowe.meetstudent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only the public subtree is ever served statically. Private files live in file.private-dir,
        // which is never registered here and is reachable only through GET /api/v1/media/{id}.
        String publicPath = Paths.get(uploadDir, "public").toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/public/**").addResourceLocations(publicPath);
    }
}
```

- [ ] **Step 6: Update security rules (WebSecurityConfig)**

In `src/main/java/com/bowe/meetstudent/security/WebSecurityConfig.java`, replace the single `/uploads/**` permitAll line and add media rules. Change:
```java
                                .requestMatchers("/uploads/**").permitAll()
```
to:
```java
                                .requestMatchers("/uploads/public/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/media/*").permitAll()
```
And add, in the authenticated section (before `.anyRequest().authenticated()`), nothing else is required — `POST/DELETE /api/v1/media/**`, `GET /api/v1/media/mine`, the ADMIN `GET /api/v1/media` and `PATCH .../verification` fall through to `anyRequest().authenticated()` plus the method-security `@PreAuthorize` on the controller. Confirm the existing `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` stays.

> Rationale: `GET /api/v1/media/*` is `permitAll` at the filter so PUBLIC media reaches anonymous users; the owner/admin check for PRIVATE media happens in `MediaService.getAccessibleMedia`. The `/*` (single segment) keeps `/api/v1/media/mine` — also a single segment — authenticated? No: `mine` matches `/api/v1/media/*` too. To avoid exposing `/mine` to anonymous, place the `mine` matcher first:
```java
                                .requestMatchers(HttpMethod.GET, "/api/v1/media/mine").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/v1/media/*").permitAll()
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./mvnw test-compile failsafe:integration-test failsafe:verify -Dit.test=MediaControllerIntegrationTests`
Expected: PASS (9 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/controllers/MediaController.java src/main/java/com/bowe/meetstudent/config/WebConfig.java src/main/java/com/bowe/meetstudent/security/WebSecurityConfig.java src/main/resources/application*.yml src/test/java/com/bowe/meetstudent/integration/controllers/MediaControllerIntegrationTests.java
git commit -m "feat(media): new media endpoints (upload/download/mine/moderation/delete) and access rules"
```

---

## Task 9: Decouple UserEntity/UserService/UserDTO from media strings

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/entities/UserEntity.java`
- Modify: `src/main/java/com/bowe/meetstudent/services/UserService.java`
- Modify: `src/main/java/com/bowe/meetstudent/dto/UserDTO.java`
- Modify: `src/main/java/com/bowe/meetstudent/dto/UpdateProfileRequest.java`
- Modify: `src/main/java/com/bowe/meetstudent/mappers/implementations/UserMapper.java`
- Modify: `src/main/java/com/bowe/meetstudent/controllers/UserController.java`
- Modify: `src/main/java/com/bowe/meetstudent/services/MediaService.java` (remove dead URL/entityType helpers)
- Test: `src/test/java/com/bowe/meetstudent/unit/services/UserServiceTest.java`, `src/test/java/com/bowe/meetstudent/integration/controllers/UserControllerIntegrationTests.java`

**Interfaces:**
- Consumes: `MediaService.findOwnedBy(Integer)` (Task 6), `MediaMapper` (Task 7).
- Produces: `UserEntity` without `diplomas`/`certificates`/`presentationVideoUrl` fields; `UserService.patch` no longer touches media; `UserDTO.diplomas`/`certificates` become `List<MediaDTO>`, `presentationVideo` becomes `MediaDTO`, populated by `UserMapper` via a `MediaService` lookup.

> This task removes fields other code reads. Work compile-error-driven: change the entity, then fix every reference the compiler flags.

- [ ] **Step 1: Update the failing tests first**

In `src/test/java/com/bowe/meetstudent/unit/services/UserServiceTest.java`, delete `testPatch` (it asserts media handling that moves out of `UserService`) and its media-related setup (`user.setDiplomas`, `user.setCertificates`, `user.setPresentationVideoUrl` in `setUp`). Keep the role/wishlist/register/resolve tests. Remove the now-unused `mediaService` mock stubs referencing `deleteRemovedMedia`/`deleteOldMediaIfChanged` — but keep the `@Mock MediaService mediaService` field (UserService may still hold the dependency for deletion of a user's owned media; see Step 4).

In `src/test/java/com/bowe/meetstudent/integration/controllers/UserControllerIntegrationTests.java`, update `testThatUserPatchUpdatesNewFields` to assert on non-media profile fields only (e.g. `firstname`, `qualification`), since diplomas/certs/video are no longer set through the user patch:
```java
    @Test
    void testThatUserPatchUpdatesNewFields() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        com.bowe.meetstudent.dto.UpdateProfileRequest updates = com.bowe.meetstudent.dto.UpdateProfileRequest.builder()
                .firstname("Renamed")
                .qualification("Data Science")
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(MockMvcResultMatchers.status().isOk())
         .andExpect(MockMvcResultMatchers.jsonPath("$.firstname").value("Renamed"))
         .andExpect(MockMvcResultMatchers.jsonPath("$.qualification").value("Data Science"));
    }
```

- [ ] **Step 2: Run tests to verify they fail/compile-break**

Run: `./mvnw test-compile`
Expected: FAIL — references to removed fields once Step 3 lands; run after Step 3. (This step confirms the baseline compiles before edits.)

- [ ] **Step 3: Remove media string fields from UserEntity**

In `src/main/java/com/bowe/meetstudent/entities/UserEntity.java`, delete the `diplomas`, `certificates`, and `presentationVideoUrl` fields (and the now-unused `@JdbcTypeCode`/`List` imports if unused). Result keeps `firstname`, `lastname`, `birthday`, `email`, `password`, `role`, `qualification`, `wishlist`.

- [ ] **Step 4: Fix UserService.patch and deleteUser**

In `src/main/java/com/bowe/meetstudent/services/UserService.java`:
- In `patch(...)`, remove the diploma/certificate/video blocks (the `mediaService.deleteRemovedMedia`, `deleteOldMediaIfChanged`, and the `setDiplomas`/`setCertificates`/`setPresentationVideoUrl` lines and the `allowRoleUpdate` role line stays). Keep firstname/lastname/email/birthday/qualification/password mapping and the role line.
- In `deleteUser(...)`, remove the diploma/certificate/video cleanup loop. Replace with cleanup of the user's owned media via the new API:
```java
    @Transactional
    public UserEntity deleteUser(int id) {
        Optional<UserEntity> toDeleteOpt = this.userRepository.findById(id);
        if (toDeleteOpt.isPresent()) {
            UserEntity toDelete = toDeleteOpt.get();
            mediaService.deleteAllOwnedBy(id);
            this.userRepository.deleteById(id);
            return toDelete;
        }
        return null;
    }
```
Add to `MediaService` (Task 6 area):
```java
    @Transactional
    public void deleteAllOwnedBy(Integer ownerId) {
        for (Media media : mediaRepository.findByOwnerId(ownerId)) {
            try {
                storageService.delete(media.getStorageKey());
            } catch (IOException e) {
                // best-effort file cleanup; the row is still removed
            }
        }
        mediaRepository.deleteAll(mediaRepository.findByOwnerId(ownerId));
    }
```

- [ ] **Step 5: Update UserDTO, UpdateProfileRequest, UserMapper, UserController**

- `src/main/java/com/bowe/meetstudent/dto/UpdateProfileRequest.java`: remove `diplomas`, `certificates`, `presentationVideoUrl` fields (keep firstname/lastname/email/birthday/password/qualification).
- `src/main/java/com/bowe/meetstudent/controllers/UserController.java`: in `toProfileUpdates(...)`, remove the `.diplomas(...)`, `.certificates(...)`, `.presentationVideoUrl(...)` builder lines.
- `src/main/java/com/bowe/meetstudent/dto/UserDTO.java`: change `private List<String> diplomas;` → `private List<MediaDTO> diplomas;`, `private List<String> certificates;` → `private List<MediaDTO> certificates;`, and `private String presentationVideoUrl;` → `private MediaDTO presentationVideo;`. Add import for `MediaDTO`. Remove `photoUrl` only if unused elsewhere; otherwise leave it.
- `src/main/java/com/bowe/meetstudent/mappers/implementations/UserMapper.java`: inject `MediaService` and `MediaMapper`; after mapping the base DTO, populate media lists from `mediaService.findByOwnerIdAndCategory`:
```java
@Component
@RequiredArgsConstructor
public class UserMapper implements Mapper<UserEntity, UserDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final Mapper<Media, MediaDTO> mediaMapper;

    @Override
    public UserDTO toDTO(UserEntity userEntity) {
        UserDTO dto = modelMapper.map(userEntity, UserDTO.class);
        dto.setPassword(null);
        dto.setConfirmedPassword(null);
        if (userEntity.getId() != null) {
            dto.setDiplomas(mediaService.findByOwnerIdAndCategory(userEntity.getId(), MediaCategory.DIPLOMA)
                    .stream().map(mediaMapper::toDTO).toList());
            dto.setCertificates(mediaService.findByOwnerIdAndCategory(userEntity.getId(), MediaCategory.CERTIFICATE)
                    .stream().map(mediaMapper::toDTO).toList());
            mediaService.findByOwnerIdAndCategory(userEntity.getId(), MediaCategory.PRESENTATION_VIDEO)
                    .stream().findFirst().ifPresent(m -> dto.setPresentationVideo(mediaMapper.toDTO(m)));
        }
        return dto;
    }

    @Override
    public UserEntity toEntity(UserDTO userDTO) {
        return modelMapper.map(userDTO, UserEntity.class);
    }
}
```
Add `MediaService.findByOwnerIdAndCategory(Integer ownerId, MediaCategory category)` (delegates to `mediaRepository.findByOwnerIdAndCategory`).

- [ ] **Step 6: Remove dead code from MediaService**

Now that no caller uses them, delete from `MediaService`: `saveMedia(MultipartFile, String)`, `deleteMedia`, `deleteMediaByUrl`, `deleteOldMediaIfChanged`, `deleteRemovedMedia`, `isAllowedEntityType`, `validateEntityType`, `ALLOWED_ENTITY_TYPES`, `uploadDir`, `getUploadBasePath`, `ensurePathInsideUploadDir`. Keep `validateFile`, `validateFileContent`, `startsWith`, `normalizeContentType`, `ALLOWED_MIME_TYPES_BY_EXTENSION`, `maxUploadBytes`, and the new upload/download/moderation methods.

- [ ] **Step 7: Run the full suite**

Run: `./mvnw verify`
Expected: BUILD SUCCESS. Fix any remaining compile references the compiler flags (e.g. leftover `getDiplomas()` calls) by removing them.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(media): move user documents to Media entity, drop legacy string fields"
```

---

## Task 10: MediaMigrationRunner (relocate existing private files)

**Files:**
- Create: `src/main/java/com/bowe/meetstudent/config/MediaMigrationRunner.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/config/MediaMigrationRunnerTest.java`

**Interfaces:**
- Consumes: `MediaRepository` (Task 1), `MediaStorageService` (Task 3), `MediaVisibility`.
- Produces: an `ApplicationRunner` that, for each PRIVATE `Media` whose `storageKey` does NOT already start with `private/`, moves the file from the legacy public location into the private dir and rewrites `storageKey` to `private/<uuid>.<ext>`. Idempotent: rows already prefixed `private/` are skipped.

> The migration SQL (Task 2) inserts rows with the legacy relative path as `storage_key` (e.g. `users/uuid.pdf`). This runner performs the physical relocation and normalizes the key. It must be safe to run repeatedly and a no-op when there is nothing to move (fresh DBs, test profile).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/bowe/meetstudent/unit/config/MediaMigrationRunnerTest.java`:
```java
package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.MediaMigrationRunner;
import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaCategory;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaMigrationRunnerTest {

    @TempDir Path tempDir;
    MediaRepository mediaRepository;
    MediaMigrationRunner runner;

    @BeforeEach
    void setUp() {
        mediaRepository = mock(MediaRepository.class);
        runner = new MediaMigrationRunner(mediaRepository);
        ReflectionTestUtils.setField(runner, "publicDir", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(runner, "privateDir", tempDir.resolve("storage/private").toString());
    }

    @Test
    void movesLegacyPrivateFileAndRewritesKey() throws Exception {
        Path legacy = tempDir.resolve("uploads/users/old.pdf");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, "doc".getBytes());

        Media media = Media.builder().storageKey("users/old.pdf")
                .visibility(MediaVisibility.PRIVATE).category(MediaCategory.DIPLOMA).ownerId(7).build();
        when(mediaRepository.findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, "private/"))
                .thenReturn(List.of(media));

        runner.run(null);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        String newKey = captor.getValue().getStorageKey();
        assertTrue(newKey.startsWith("private/"), newKey);
        assertTrue(Files.exists(tempDir.resolve("storage/private").resolve(newKey.substring("private/".length()))));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void doesNothingWhenNoLegacyRows() throws Exception {
        when(mediaRepository.findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, "private/"))
                .thenReturn(List.of());

        runner.run(null);

        verify(mediaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 2: Add the repository query**

Add to `MediaRepository`:
```java
    List<Media> findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility visibility, String prefix);
```
(import `com.bowe.meetstudent.entities.enums.MediaVisibility`.)

- [ ] **Step 3: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaMigrationRunnerTest`
Expected: FAIL — `MediaMigrationRunner` does not exist.

- [ ] **Step 4: Implement the runner**

`src/main/java/com/bowe/meetstudent/config/MediaMigrationRunner.java`:
```java
package com.bowe.meetstudent.config;

import com.bowe.meetstudent.entities.Media;
import com.bowe.meetstudent.entities.enums.MediaVisibility;
import com.bowe.meetstudent.repositories.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MediaMigrationRunner implements ApplicationRunner {

    private static final String PRIVATE_PREFIX = "private/";

    private final MediaRepository mediaRepository;

    @Value("${file.upload-dir:uploads}")
    private String publicDir;

    @Value("${file.private-dir:storage/private}")
    private String privateDir;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var legacy = mediaRepository
                .findByVisibilityAndStorageKeyNotStartingWith(MediaVisibility.PRIVATE, PRIVATE_PREFIX);
        Path publicBase = Paths.get(publicDir).toAbsolutePath().normalize();
        Path privateBase = Paths.get(privateDir).toAbsolutePath().normalize();

        for (Media media : legacy) {
            Path source = publicBase.resolve(media.getStorageKey()).normalize();
            if (!source.startsWith(publicBase) || !Files.exists(source)) {
                continue;
            }
            String ext = extensionOf(media.getStorageKey());
            String fileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path target = privateBase.resolve(fileName);
            Files.createDirectories(privateBase);
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            media.setStorageKey(PRIVATE_PREFIX + fileName);
            mediaRepository.save(media);
        }
    }

    private String extensionOf(String key) {
        int dot = key.lastIndexOf('.');
        return dot >= 0 && dot < key.length() - 1 ? key.substring(dot + 1) : "";
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaMigrationRunnerTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/config/MediaMigrationRunner.java src/main/java/com/bowe/meetstudent/repositories/MediaRepository.java src/test/java/com/bowe/meetstudent/unit/config/MediaMigrationRunnerTest.java
git commit -m "feat(media): relocate legacy private files into private dir on startup"
```

---

## Task 11: Full suite + docs

**Files:**
- Modify: `CLAUDE.md` (media architecture note)
- Modify: `Readme.md` (media endpoints, breaking changes)

- [ ] **Step 1: Run the full suite**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all unit + integration tests green.

- [ ] **Step 2: Update docs**

Add a short "Media & documents" subsection to `CLAUDE.md` (under Architecture) describing: `Media` entity, public (`uploads/public/`, static) vs private (`storage/private/`, endpoint-only) storage, `MediaStorageService` vs `MediaService` split, upload categories/roles, moderation statuses. Add to `Readme.md` the new endpoints and the breaking change (diplomas/certificates/presentation video are now media ids returned as `MediaDTO`, uploaded via `POST /api/v1/media?category=...`).

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md Readme.md
git commit -m "docs: document media access control architecture and endpoints"
```

---

## Task 12: Deploy-time verification (Postgres migrations)

> Not a code task — the gate that proves Task 2's migrations are correct, since the H2 test suite does not run Flyway.

- [ ] **Step 1: Boot against Postgres**

Start the docker Postgres and run the app with the docker profile so Flyway applies V14/V15 and Hibernate `ddl-auto: validate` checks the `media` mapping:
```bash
docker compose up -d db   # or the compose service that provides Postgres
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run
```
Expected: application starts with no Flyway error and no Hibernate schema-validation error (`Schema-validation: ...`). If validation fails, reconcile the V14 column definitions with `Media` and re-run.

- [ ] **Step 2: Sanity-check the data migration**

With a DB that had legacy `users.diplomas`/`certificates` data, confirm `SELECT category, count(*) FROM media GROUP BY category;` shows migrated rows and that the app served a private document via `GET /api/v1/media/{id}` as its owner. Confirm the legacy columns are gone (`\d users`).

---

## Self-Review Notes

- **Spec coverage:** Media entity + fields (Task 1); public/private split & separate dirs (Tasks 3, 8); role-gated upload STUDENT/EXPERT/ADMIN vs ADMIN-only school media (Tasks 4, 8); owner+admin download authz (Tasks 5, 8); moderation PENDING→VERIFIED/REJECTED + reason, non-blocking (Tasks 6, 8); owner listing `/mine` (Tasks 6, 8); idempotent upload (Tasks 4, 8); atomic write/no orphan (Task 3); migrations + file relocation (Tasks 2, 10); decoupling of user string fields (Task 9); security rules & static-serving restriction (Task 8); deploy verification (Task 12). All spec sections map to a task.
- **Out of scope (spec):** resumable chunked upload (Phase 2), multi-file upload, REJECTED functional blocking, antivirus, remote object storage — none implemented, as intended.
- **Type consistency:** `MediaCategory`/`MediaVisibility`/`VerificationStatus` names are used identically across tasks; `MediaService` method names (`upload`, `assertCanUpload`, `getAccessibleMedia`, `loadContent`, `setVerification`, `findByStatus`, `findOwnedBy`, `findByOwnerIdAndCategory`, `delete`, `deleteAllOwnedBy`) are consistent between definition and callers; repository method names match their usages.
