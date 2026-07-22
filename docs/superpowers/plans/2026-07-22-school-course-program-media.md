# School / Course / Program Media Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `School`, `Course`, and `Program` a working image-upload path by replacing their orphaned URL-string fields with `media_id` foreign keys into the `Media` table.

**Architecture:** Public images become first-class `Media` rows uploaded through the existing `POST /api/v1/media?category=...` flow. Each entity stores a nullable `Integer` FK (`logoMediaId`, `coverMediaId`, `photoMediaId`). Mappers resolve those ids to a `MediaDTO` (carrying a new relative `publicUrl`) via `MediaService`, mirroring the established `UserMapper`-injects-`MediaService` decoupling. Request DTOs carry the id; response DTOs carry the resolved object.

**Tech Stack:** Spring Boot 3, Java 21, Spring Data JPA, ModelMapper, Flyway (Postgres), H2 (tests), JUnit 5 + Mockito + MockMvc.

## Global Constraints

- Java local variables: prefer `var` for clearly inferred types.
- Constructor injection via Lombok `@RequiredArgsConstructor`.
- Unit test classes end in `Test`; integration test classes end in `IntegrationTests` (build enforces this).
- Integration tests use full versioned paths (`/api/v1/...`) and `TestDataUtil.mockUser("ROLE_ADMIN")` — never `jwt().authorities(...)`.
- H2 test suite runs with Flyway disabled and `ddl-auto: create-drop`; schema comes from entities. All schema changes ALSO go in a Flyway `V<n>__*.sql` for Postgres.
- Never use `@Column(columnDefinition = "text[]")` (breaks H2).
- Run `./mvnw clean verify` and keep it green. Commit after each task.
- Public storage keys have the form `public/<uuid>.<ext>` and are served under `/uploads/public/**`.

---

### Task 1: Add `COURSE_PHOTO` / `PROGRAM_PHOTO` media categories

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/entities/enums/MediaCategory.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/entities/MediaCategoryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `MediaCategory.COURSE_PHOTO`, `MediaCategory.PROGRAM_PHOTO` — both `PUBLIC`, non-moderated, allowed upload role `Set.of("ROLE_ADMIN")`.

- [ ] **Step 1: Write the failing test**

Add to `MediaCategoryTest`:

```java
@Test
void coursePhotoAndProgramPhotoArePublicNonModeratedAdminOnly() {
    for (MediaCategory c : new MediaCategory[]{MediaCategory.COURSE_PHOTO, MediaCategory.PROGRAM_PHOTO}) {
        assertEquals(MediaVisibility.PUBLIC, c.getVisibility(), c.name());
        assertFalse(c.isModerated(), c.name());
        assertFalse(c.isPersonalDocument(), c.name());
        assertEquals(java.util.Set.of("ROLE_ADMIN"), c.getAllowedUploadRoles(), c.name());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=MediaCategoryTest#coursePhotoAndProgramPhotoArePublicNonModeratedAdminOnly`
Expected: FAIL — compile error, `COURSE_PHOTO`/`PROGRAM_PHOTO` do not exist.

- [ ] **Step 3: Add the enum constants**

In `MediaCategory.java`, add after `SCHOOL_COVER(...)` and before `USER_PHOTO(...)`:

```java
    COURSE_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
    PROGRAM_PHOTO(MediaVisibility.PUBLIC, false, Set.of("ROLE_ADMIN")),
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=MediaCategoryTest`
Expected: PASS (all methods).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/entities/enums/MediaCategory.java src/test/java/com/bowe/meetstudent/unit/entities/MediaCategoryTest.java
git commit -m "feat(media): add COURSE_PHOTO and PROGRAM_PHOTO categories"
```

---

### Task 2: Add `MediaDTO.publicUrl` derived in `MediaMapper`

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/dto/MediaDTO.java`
- Modify: `src/main/java/com/bowe/meetstudent/mappers/implementations/MediaMapper.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/mappers/MediaMapperTest.java`

**Interfaces:**
- Consumes: `Media.getStorageKey()`, `Media.getVisibility()`.
- Produces: `MediaDTO.getPublicUrl()` — `"/uploads/" + storageKey` for `PUBLIC` media, `null` for `PRIVATE`.

- [ ] **Step 1: Write the failing tests**

Add to `MediaMapperTest`:

```java
@Test
void toDtoSetsPublicUrlForPublicMedia() {
    Media media = Media.builder()
            .storageKey("public/logo.png")
            .category(MediaCategory.SCHOOL_LOGO)
            .visibility(MediaVisibility.PUBLIC)
            .build();
    ReflectionTestUtils.setField(media, "id", 5);

    MediaDTO dto = mapper.toDTO(media);

    assertEquals("/uploads/public/logo.png", dto.getPublicUrl());
}

@Test
void toDtoLeavesPublicUrlNullForPrivateMedia() {
    Media media = Media.builder()
            .storageKey("private/secret.pdf")
            .category(MediaCategory.DIPLOMA)
            .visibility(MediaVisibility.PRIVATE)
            .build();

    MediaDTO dto = mapper.toDTO(media);

    assertNull(dto.getPublicUrl());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=MediaMapperTest`
Expected: FAIL — `getPublicUrl()` does not exist (compile error).

- [ ] **Step 3: Add the field and mapping**

In `MediaDTO.java`, add a field after `sizeBytes`:

```java
    private String publicUrl;
```

In `MediaMapper.java`, replace `toDTO` with:

```java
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
                .publicUrl(publicUrl(media))
                .build();
    }

    private String publicUrl(Media media) {
        if (media.getVisibility() == com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC
                && media.getStorageKey() != null) {
            return "/uploads/" + media.getStorageKey();
        }
        return null;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=MediaMapperTest`
Expected: PASS (all methods, including the existing `toDtoCopiesSafeFieldsAndOmitsStorageKey`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/dto/MediaDTO.java src/main/java/com/bowe/meetstudent/mappers/implementations/MediaMapper.java src/test/java/com/bowe/meetstudent/unit/mappers/MediaMapperTest.java
git commit -m "feat(media): expose relative publicUrl on MediaDTO for public media"
```

---

### Task 3: Add `MediaService.findById` and `MediaService.deleteById`

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/services/MediaService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java`

**Interfaces:**
- Consumes: `MediaRepository.findById(Integer)`, `MediaRepository.delete(Media)`, `MediaStorageService.delete(String)`.
- Produces:
  - `Optional<Media> findById(Integer mediaId)` — passthrough to repository; returns empty for `null` id.
  - `void deleteById(Integer mediaId)` — server-side (no principal check); deletes the file then the row; no-op if id is `null` or not found; file-delete failure does not throw.

- [ ] **Step 1: Write the failing tests**

Add to `MediaServiceTest`:

```java
// --- deleteById / findById ---

@Test
void findByIdReturnsEmptyForNullId() {
    assertTrue(mediaService.findById(null).isEmpty());
    verifyNoInteractions(mediaRepository);
}

@Test
void deleteByIdDeletesFileAndRow() throws IOException {
    Media media = Media.builder().storageKey("public/x.png")
            .visibility(MediaVisibility.PUBLIC).category(MediaCategory.SCHOOL_LOGO).build();
    ReflectionTestUtils.setField(media, "id", 9);
    when(mediaRepository.findById(9)).thenReturn(Optional.of(media));

    mediaService.deleteById(9);

    verify(storageService).delete("public/x.png");
    verify(mediaRepository).delete(media);
}

@Test
void deleteByIdIsNoOpWhenNotFound() throws IOException {
    when(mediaRepository.findById(404)).thenReturn(Optional.empty());

    mediaService.deleteById(404);

    verify(storageService, never()).delete(any());
    verify(mediaRepository, never()).delete(any());
}

@Test
void deleteByIdIsNoOpForNullId() throws IOException {
    mediaService.deleteById(null);
    verifyNoInteractions(mediaRepository);
    verify(storageService, never()).delete(any());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: FAIL — `findById`/`deleteById` do not exist (compile error).

- [ ] **Step 3: Add the methods**

In `MediaService.java`, add:

```java
    public Optional<Media> findById(Integer mediaId) {
        if (mediaId == null) return Optional.empty();
        return mediaRepository.findById(mediaId);
    }

    /**
     * Server-side delete of a media row and its file, without a principal check.
     * For orchestration by ROLE_ADMIN-gated entity endpoints (school/course/program
     * image replace + delete). No-op when the id is null or the row is gone.
     */
    @Transactional
    public void deleteById(Integer mediaId) {
        if (mediaId == null) return;
        mediaRepository.findById(mediaId).ifPresent(media -> {
            try {
                storageService.delete(media.getStorageKey());
            } catch (IOException e) {
                // best-effort file cleanup; the row is still removed
            }
            mediaRepository.delete(media);
        });
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=MediaServiceTest`
Expected: PASS (all methods).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/services/MediaService.java src/test/java/com/bowe/meetstudent/unit/services/MediaServiceTest.java
git commit -m "feat(media): add findById and server-side deleteById for orchestration"
```

---

### Task 4: School — replace URL fields with `logoMediaId` / `coverMediaId`

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/entities/School.java`
- Modify: `src/main/java/com/bowe/meetstudent/dto/SchoolDTO.java`
- Modify: `src/main/java/com/bowe/meetstudent/mappers/implementations/SchoolMapper.java`
- Modify: `src/main/java/com/bowe/meetstudent/services/SchoolService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/SchoolServiceTest.java`
- Test: `src/test/java/com/bowe/meetstudent/integration/controllers/SchoolControllerIntegrationTests.java`

**Interfaces:**
- Consumes: `MediaService.findById`, `MediaService.deleteById`, `MediaMapper.toDTO`, `MediaCategory.SCHOOL_LOGO/SCHOOL_COVER`, `MediaDTO`.
- Produces:
  - `School.getLogoMediaId()/setLogoMediaId(Integer)`, `School.getCoverMediaId()/setCoverMediaId(Integer)` (columns `logo_media_id`, `cover_media_id`); `logoUrl`/`coverPhotoUrl` removed.
  - `SchoolDTO`: input `logoMediaId`, `coverMediaId` (Integer); output `logo`, `cover` (`MediaDTO`); `logoUrl`/`coverPhotoUrl` removed.
  - `SchoolMapper.toDTO` resolves ids to `MediaDTO`; `toEntity` copies ids through.
  - `SchoolService.patch` deletes a replaced media id; `SchoolService.delete` deletes referenced media ids.

- [ ] **Step 1: Update entity fields**

In `School.java`, replace:

```java
    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_photo_url")
    private String coverPhotoUrl;
```

with:

```java
    @Column(name = "logo_media_id")
    private Integer logoMediaId;

    @Column(name = "cover_media_id")
    private Integer coverMediaId;
```

- [ ] **Step 2: Update DTO fields**

In `SchoolDTO.java`, replace:

```java
    private String logoUrl;
    private String coverPhotoUrl;
```

with:

```java
    // input: ids of already-uploaded media
    private Integer logoMediaId;
    private Integer coverMediaId;
    // output: resolved media objects (with publicUrl)
    private com.bowe.meetstudent.dto.MediaDTO logo;
    private com.bowe.meetstudent.dto.MediaDTO cover;
```

- [ ] **Step 3: Rewrite the mapper (media resolution)**

Replace `SchoolMapper.java` body with:

```java
package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolMapper implements Mapper<School, SchoolDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public SchoolDTO toDTO(School school) {
        SchoolDTO dto = modelMapper.map(school, SchoolDTO.class);
        dto.setLogoMediaId(school.getLogoMediaId());
        dto.setCoverMediaId(school.getCoverMediaId());
        dto.setLogo(mediaService.findById(school.getLogoMediaId()).map(mediaMapper::toDTO).orElse(null));
        dto.setCover(mediaService.findById(school.getCoverMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public School toEntity(SchoolDTO schoolDTO) {
        School school = modelMapper.map(schoolDTO, School.class);
        school.setLogoMediaId(schoolDTO.getLogoMediaId());
        school.setCoverMediaId(schoolDTO.getCoverMediaId());
        if (school.getPrograms() != null) {
            school.getPrograms().forEach(p -> {
                p.setSchool(school);
                if (p.getCourses() != null) {
                    p.getCourses().forEach(c -> c.setProgram(p));
                }
            });
        }
        return school;
    }
}
```

Note: the explicit `set...MediaId` calls are authoritative and guard against ModelMapper LOOSE matching the `logo`/`cover` `MediaDTO` fields onto the id columns.

- [ ] **Step 4: Update the service (replace + delete cleanup)**

In `SchoolService.java`, replace the `delete` method body's media lines and the `patch` media lines.

Replace in `delete`:

```java
            mediaService.deleteMediaByUrl(school.getLogoUrl());
            mediaService.deleteMediaByUrl(school.getCoverPhotoUrl());
```

with:

```java
            mediaService.deleteById(school.getLogoMediaId());
            mediaService.deleteById(school.getCoverMediaId());
```

Replace the media block in `patch`:

```java
            mediaService.deleteOldMediaIfChanged(existing.getLogoUrl(), updates.getLogoUrl());
            mediaService.deleteOldMediaIfChanged(existing.getCoverPhotoUrl(), updates.getCoverPhotoUrl());
```

with:

```java
            if (updates.getLogoMediaId() != null
                    && !updates.getLogoMediaId().equals(existing.getLogoMediaId())) {
                mediaService.deleteById(existing.getLogoMediaId());
            }
            if (updates.getCoverMediaId() != null
                    && !updates.getCoverMediaId().equals(existing.getCoverMediaId())) {
                mediaService.deleteById(existing.getCoverMediaId());
            }
```

And replace the field-copy lines:

```java
            if (updates.getLogoUrl() != null) existing.setLogoUrl(updates.getLogoUrl());
            if (updates.getCoverPhotoUrl() != null) existing.setCoverPhotoUrl(updates.getCoverPhotoUrl());
```

with:

```java
            if (updates.getLogoMediaId() != null) existing.setLogoMediaId(updates.getLogoMediaId());
            if (updates.getCoverMediaId() != null) existing.setCoverMediaId(updates.getCoverMediaId());
```

- [ ] **Step 5: Rewrite the unit test**

Replace `SchoolServiceTest.java` `setUp` and both tests:

```java
    @BeforeEach
    void setUp() {
        school = new School();
        school.setId(1);
        school.setName("Test School");
        school.setLogoMediaId(10);
        school.setCoverMediaId(11);
    }

    @Test
    void testDeleteRemovesReferencedMedia() {
        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.doNothing().when(schoolRepository).deleteById(1);

        schoolService.delete(1);

        Mockito.verify(mediaService).deleteById(10);
        Mockito.verify(mediaService).deleteById(11);
        Mockito.verify(schoolRepository).deleteById(1);
    }

    @Test
    void testPatchReplacesLogoMediaAndDeletesOld() {
        School updates = School.builder().name("Updated School").logoMediaId(20).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        assertEquals("Updated School", school.getName());
        assertEquals(20, school.getLogoMediaId());
        Mockito.verify(mediaService).deleteById(10); // old logo removed
        Mockito.verify(mediaService, Mockito.never()).deleteById(11); // cover unchanged
    }

    @Test
    void testPatchWithSameLogoMediaIdDoesNotDelete() {
        School updates = School.builder().logoMediaId(10).build();

        Mockito.when(schoolRepository.findById(1)).thenReturn(Optional.of(school));
        Mockito.when(schoolRepository.save(any(School.class))).thenReturn(school);

        schoolService.patch(1, updates);

        Mockito.verify(mediaService, Mockito.never()).deleteById(any());
    }
```

- [ ] **Step 6: Run unit tests to verify they pass**

Run: `./mvnw test -Dtest=SchoolServiceTest`
Expected: PASS.

- [ ] **Step 7: Add an integration test (upload → link → resolve; delete removes media)**

In `SchoolControllerIntegrationTests.java`, autowire the repository at the top with the other `@Autowired` fields:

```java
    @Autowired
    private com.bowe.meetstudent.repositories.MediaRepository mediaRepository;
```

Add these tests (helper builds a persisted public logo Media row):

```java
    private com.bowe.meetstudent.entities.Media persistedLogo() {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/logo-it.png")
                .originalFilename("logo.png").contentType("image/png").sizeBytes(10L)
                .category(com.bowe.meetstudent.entities.enums.MediaCategory.SCHOOL_LOGO)
                .visibility(com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC)
                .build();
        return mediaRepository.save(media);
    }

    @Test
    void putSchoolWithLogoMediaIdResolvesLogoWithPublicUrl() throws Exception {
        School saved = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        School school = schoolService.save(saved);
        var media = persistedLogo();

        SchoolDTO body = TestDataUtil.createSchoolDto();
        body.setLogoMediaId(media.getId());
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logo.id").value(media.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.logo.publicUrl").value("/uploads/public/logo-it.png"));
    }

    @Test
    void deleteSchoolRemovesReferencedLogoMedia() throws Exception {
        var media = persistedLogo();
        School saved = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        saved.setLogoMediaId(media.getId());
        School school = schoolService.save(saved);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(mediaRepository.findById(media.getId()).isEmpty());
    }
```

- [ ] **Step 8: Run the school integration tests**

Run: `./mvnw verify -Dit.test=SchoolControllerIntegrationTests -Dtest=skip -DfailIfNoTests=false`
Expected: PASS (existing + new).

- [ ] **Step 9: Full build**

Run: `./mvnw clean verify`
Expected: BUILD SUCCESS.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/entities/School.java src/main/java/com/bowe/meetstudent/dto/SchoolDTO.java src/main/java/com/bowe/meetstudent/mappers/implementations/SchoolMapper.java src/main/java/com/bowe/meetstudent/services/SchoolService.java src/test/java/com/bowe/meetstudent/unit/services/SchoolServiceTest.java src/test/java/com/bowe/meetstudent/integration/controllers/SchoolControllerIntegrationTests.java
git commit -m "feat(school): reference logo/cover media by FK instead of URL strings"
```

---

### Task 5: Program — replace `photoUrl` with `photoMediaId`

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/entities/Program.java`
- Modify: `src/main/java/com/bowe/meetstudent/dto/ProgramDTO.java`
- Modify: `src/main/java/com/bowe/meetstudent/mappers/implementations/ProgramMapper.java`
- Modify: `src/main/java/com/bowe/meetstudent/services/ProgramService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/ProgramServiceTest.java`
- Test: `src/test/java/com/bowe/meetstudent/integration/controllers/ProgramControllerIntegrationTests.java`

**Interfaces:**
- Consumes: `MediaService.findById/deleteById`, `MediaMapper.toDTO`, `MediaCategory.PROGRAM_PHOTO`.
- Produces: `Program.getPhotoMediaId()/setPhotoMediaId(Integer)` (column `photo_media_id`); `ProgramDTO` input `photoMediaId` + output `photo` (`MediaDTO`); mapper resolves; service replace/delete cleanup.

- [ ] **Step 1: Update entity**

In `Program.java`, replace:

```java
    @Column(name = "photo_url")
    private String photoUrl;
```

with:

```java
    @Column(name = "photo_media_id")
    private Integer photoMediaId;
```

- [ ] **Step 2: Update DTO**

In `ProgramDTO.java`, replace `private String photoUrl;` with:

```java
    private Integer photoMediaId;
    private com.bowe.meetstudent.dto.MediaDTO photo;
```

- [ ] **Step 3: Rewrite the mapper**

Replace `ProgramMapper.java` with:

```java
package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProgramMapper implements Mapper<Program, ProgramDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public ProgramDTO toDTO(Program program) {
        ProgramDTO dto = modelMapper.map(program, ProgramDTO.class);
        dto.setPhotoMediaId(program.getPhotoMediaId());
        dto.setPhoto(mediaService.findById(program.getPhotoMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public Program toEntity(ProgramDTO programDTO) {
        Program program = modelMapper.map(programDTO, Program.class);
        program.setPhotoMediaId(programDTO.getPhotoMediaId());
        if (programDTO.getSchoolId() == null) {
            program.setSchool(null);
        }
        if (program.getCourses() != null) {
            program.getCourses().forEach(c -> c.setProgram(program));
        }
        return program;
    }
}
```

- [ ] **Step 4: Update the service**

In `ProgramService.java` `delete`, replace:

```java
            mediaService.deleteMediaByUrl(program.getPhotoUrl());
```

with:

```java
            mediaService.deleteById(program.getPhotoMediaId());
```

In `patch`, replace:

```java
            mediaService.deleteOldMediaIfChanged(existing.getPhotoUrl(), updates.getPhotoUrl());
```

with:

```java
            if (updates.getPhotoMediaId() != null
                    && !updates.getPhotoMediaId().equals(existing.getPhotoMediaId())) {
                mediaService.deleteById(existing.getPhotoMediaId());
            }
```

and replace:

```java
            if (updates.getPhotoUrl() != null) existing.setPhotoUrl(updates.getPhotoUrl());
```

with:

```java
            if (updates.getPhotoMediaId() != null) existing.setPhotoMediaId(updates.getPhotoMediaId());
```

- [ ] **Step 5: Update the unit test**

Open `ProgramServiceTest.java`. Find every use of `photoUrl` (setter, `deleteMediaByUrl`, `deleteOldMediaIfChanged`, assertions) and convert to the media-id model. Replace the delete-test media verification with:

```java
        Mockito.verify(mediaService).deleteById(program.getPhotoMediaId());
```

Replace the patch-test setup/verification so that: the existing program has `photoMediaId = 10`; the `updates` builder uses `.photoMediaId(20)`; assert `existing.getPhotoMediaId()` becomes `20` and `Mockito.verify(mediaService).deleteById(10)`. Add a companion test where `updates` reuses id `10` and `Mockito.verify(mediaService, Mockito.never()).deleteById(any())`. Set the existing program's `photoMediaId` in whatever `@BeforeEach`/setup builds it (replace any `.photoUrl(...)`/`setPhotoUrl(...)` with `.photoMediaId(10)`/`setPhotoMediaId(10)`).

- [ ] **Step 6: Run unit tests**

Run: `./mvnw test -Dtest=ProgramServiceTest`
Expected: PASS.

- [ ] **Step 7: Add an integration test**

In `ProgramControllerIntegrationTests.java`, autowire `MediaRepository` (as in Task 4) and add a persisted-photo helper + a PUT test asserting `$.photo.publicUrl`:

```java
    private com.bowe.meetstudent.entities.Media persistedProgramPhoto() {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/prog-it.png")
                .originalFilename("p.png").contentType("image/png").sizeBytes(10L)
                .category(com.bowe.meetstudent.entities.enums.MediaCategory.PROGRAM_PHOTO)
                .visibility(com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC)
                .build();
        return mediaRepository.save(media);
    }
```

Model the PUT/GET call on the existing create/update tests in that file (reuse whatever helper persists a program + its parent school). Assert the response JSON path `$.photo.id` equals the media id and `$.photo.publicUrl` equals `/uploads/public/prog-it.png`. Use `TestDataUtil.mockUser("ROLE_ADMIN")`.

- [ ] **Step 8: Run program integration tests**

Run: `./mvnw verify -Dit.test=ProgramControllerIntegrationTests -Dtest=skip -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/entities/Program.java src/main/java/com/bowe/meetstudent/dto/ProgramDTO.java src/main/java/com/bowe/meetstudent/mappers/implementations/ProgramMapper.java src/main/java/com/bowe/meetstudent/services/ProgramService.java src/test/java/com/bowe/meetstudent/unit/services/ProgramServiceTest.java src/test/java/com/bowe/meetstudent/integration/controllers/ProgramControllerIntegrationTests.java
git commit -m "feat(program): reference photo media by FK instead of URL string"
```

---

### Task 6: Course — replace `photoUrl` with `photoMediaId`

**Files:**
- Modify: `src/main/java/com/bowe/meetstudent/entities/Course.java`
- Modify: `src/main/java/com/bowe/meetstudent/dto/CourseDTO.java`
- Modify: `src/main/java/com/bowe/meetstudent/mappers/implementations/CourseMapper.java`
- Modify: `src/main/java/com/bowe/meetstudent/services/CourseService.java`
- Test: `src/test/java/com/bowe/meetstudent/unit/services/CourseServiceTest.java`
- Test: `src/test/java/com/bowe/meetstudent/integration/controllers/CourseControllerIntegrationTests.java`

**Interfaces:**
- Consumes: `MediaService.findById/deleteById`, `MediaMapper.toDTO`, `MediaCategory.COURSE_PHOTO`.
- Produces: `Course.getPhotoMediaId()/setPhotoMediaId(Integer)` (column `photo_media_id`); `CourseDTO` input `photoMediaId` + output `photo` (`MediaDTO`); mapper resolves; service replace/delete cleanup.

- [ ] **Step 1: Update entity**

In `Course.java`, replace:

```java
    @Column(name = "photo_url")
    private String photoUrl;
```

with:

```java
    @Column(name = "photo_media_id")
    private Integer photoMediaId;
```

- [ ] **Step 2: Update DTO**

In `CourseDTO.java`, replace `private String photoUrl;` with:

```java
    private Integer photoMediaId;
    private com.bowe.meetstudent.dto.MediaDTO photo;
```

- [ ] **Step 3: Rewrite the mapper**

Replace `CourseMapper.java` with:

```java
package com.bowe.meetstudent.mappers.implementations;

import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.services.MediaService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseMapper implements Mapper<Course, CourseDTO> {

    private final ModelMapper modelMapper;
    private final MediaService mediaService;
    private final MediaMapper mediaMapper;

    @Override
    public CourseDTO toDTO(Course course) {
        CourseDTO dto = modelMapper.map(course, CourseDTO.class);
        dto.setPhotoMediaId(course.getPhotoMediaId());
        dto.setPhoto(mediaService.findById(course.getPhotoMediaId()).map(mediaMapper::toDTO).orElse(null));
        return dto;
    }

    @Override
    public Course toEntity(CourseDTO courseDTO) {
        Course course = modelMapper.map(courseDTO, Course.class);
        course.setPhotoMediaId(courseDTO.getPhotoMediaId());
        if (courseDTO.getProgramId() == null) {
            course.setProgram(null);
        }
        return course;
    }
}
```

- [ ] **Step 4: Update the service**

In `CourseService.java` `delete`, replace:

```java
            mediaService.deleteMediaByUrl(course.getPhotoUrl());
```

with:

```java
            mediaService.deleteById(course.getPhotoMediaId());
```

In `patch`, replace:

```java
            mediaService.deleteOldMediaIfChanged(existing.getPhotoUrl(), updates.getPhotoUrl());
```

with:

```java
            if (updates.getPhotoMediaId() != null
                    && !updates.getPhotoMediaId().equals(existing.getPhotoMediaId())) {
                mediaService.deleteById(existing.getPhotoMediaId());
            }
```

and replace:

```java
            if (updates.getPhotoUrl() != null) existing.setPhotoUrl(updates.getPhotoUrl());
```

with:

```java
            if (updates.getPhotoMediaId() != null) existing.setPhotoMediaId(updates.getPhotoMediaId());
```

- [ ] **Step 5: Update the unit test**

In `CourseServiceTest.java`, apply the same conversion described in Task 5 Step 5, but for `Course`: existing course `photoMediaId = 10`; patch `updates` with `.photoMediaId(20)`; assert `deleteById(10)` on replace and a companion test asserting `never().deleteById(any())` when the id is unchanged; delete-test verifies `deleteById(course.getPhotoMediaId())`. Replace all `photoUrl`/`deleteMediaByUrl`/`deleteOldMediaIfChanged` usages.

- [ ] **Step 6: Run unit tests**

Run: `./mvnw test -Dtest=CourseServiceTest`
Expected: PASS.

- [ ] **Step 7: Add an integration test**

In `CourseControllerIntegrationTests.java`, autowire `MediaRepository` and add a persisted-photo helper (`storageKey "public/course-it.png"`, category `COURSE_PHOTO`, visibility `PUBLIC`) plus a PUT test asserting `$.photo.id` and `$.photo.publicUrl` == `/uploads/public/course-it.png`, modeled on the file's existing create/update tests. Use `TestDataUtil.mockUser("ROLE_ADMIN")`.

- [ ] **Step 8: Run course integration tests**

Run: `./mvnw verify -Dit.test=CourseControllerIntegrationTests -Dtest=skip -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 9: Full build**

Run: `./mvnw clean verify`
Expected: BUILD SUCCESS (all unit + integration).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/bowe/meetstudent/entities/Course.java src/main/java/com/bowe/meetstudent/dto/CourseDTO.java src/main/java/com/bowe/meetstudent/mappers/implementations/CourseMapper.java src/main/java/com/bowe/meetstudent/services/CourseService.java src/test/java/com/bowe/meetstudent/unit/services/CourseServiceTest.java src/test/java/com/bowe/meetstudent/integration/controllers/CourseControllerIntegrationTests.java
git commit -m "feat(course): reference photo media by FK instead of URL string"
```

---

### Task 7: Flyway migration `V16` (Postgres only)

**Files:**
- Create: `src/main/resources/db/migration/V16__reconcile_school_course_program_media.sql`

**Interfaces:**
- Consumes: existing `media`, `schools`, `courses`, `programs` tables.
- Produces: `logo_media_id`/`cover_media_id` on `schools`, `photo_media_id` on `courses` and `programs`, each with an FK to `media(id) ON DELETE SET NULL`; old URL columns dropped.

Note: the H2 test suite runs with Flyway disabled and generates its schema from the entities, so this migration has no automated test. It is verified manually against Postgres (folds into the pending "Task 12" Postgres/Flyway check on this branch).

- [ ] **Step 1: Write the migration**

```sql
-- Reconcile school/course/program image fields with the media table.
-- Replaces URL-string columns with nullable FKs into media(id).

ALTER TABLE schools  ADD COLUMN logo_media_id  INTEGER;
ALTER TABLE schools  ADD COLUMN cover_media_id INTEGER;
ALTER TABLE courses  ADD COLUMN photo_media_id INTEGER;
ALTER TABLE programs ADD COLUMN photo_media_id INTEGER;

ALTER TABLE schools
    ADD CONSTRAINT fk_schools_logo_media
        FOREIGN KEY (logo_media_id) REFERENCES media (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_schools_cover_media
        FOREIGN KEY (cover_media_id) REFERENCES media (id) ON DELETE SET NULL;

ALTER TABLE courses
    ADD CONSTRAINT fk_courses_photo_media
        FOREIGN KEY (photo_media_id) REFERENCES media (id) ON DELETE SET NULL;

ALTER TABLE programs
    ADD CONSTRAINT fk_programs_photo_media
        FOREIGN KEY (photo_media_id) REFERENCES media (id) ON DELETE SET NULL;

-- Legacy URL values are intentionally discarded (no data migration, per design).
ALTER TABLE schools  DROP COLUMN logo_url;
ALTER TABLE schools  DROP COLUMN cover_photo_url;
ALTER TABLE courses  DROP COLUMN photo_url;
ALTER TABLE programs DROP COLUMN photo_url;
```

- [ ] **Step 2: Sanity-check migration ordering**

Run: `ls src/main/resources/db/migration/`
Expected: `V16__reconcile_school_course_program_media.sql` is the highest version (after `V15`).

- [ ] **Step 3: Verify the full build is still green**

Run: `./mvnw clean verify`
Expected: BUILD SUCCESS (H2 unaffected — Flyway disabled in tests).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V16__reconcile_school_course_program_media.sql
git commit -m "feat(media): V16 migration - school/course/program media FKs"
```

---

### Task 8: Update documentation

**Files:**
- Modify: `CLAUDE.md` (the "Media & documents" section)

**Interfaces:**
- Consumes: nothing.
- Produces: doc parity with the new model.

- [ ] **Step 1: Update the Media & documents section**

In `CLAUDE.md`, under "### Media & documents", add a bullet describing the new model. Insert after the "Categories & roles" bullet:

```markdown
- **Entity images:** `School` (`logoMediaId`, `coverMediaId`), `Course` (`photoMediaId`), and `Program` (`photoMediaId`) reference public `Media` rows by FK instead of URL strings. Upload flow: `POST /api/v1/media?category=SCHOOL_LOGO|SCHOOL_COVER|COURSE_PHOTO|PROGRAM_PHOTO` (ADMIN-only) → read `MediaDTO.publicUrl` (a relative `/uploads/public/...` URL, set only on public media) → `PUT`/`PATCH` the entity with the media id. Response DTOs expose the resolved media as `logo`/`cover`/`photo` objects. Replacing or deleting an entity deletes the orphaned media (`MediaService.deleteById`); `V16` adds the FK columns (`ON DELETE SET NULL`) and drops the legacy URL columns.
```

- [ ] **Step 2: Verify no stale references remain**

Run: `grep -rn "entityType/upload\|logoUrl\|coverPhotoUrl\|photoUrl" CLAUDE.md`
Expected: no matches referring to the removed model in the Media section (the `UserDTO.photoUrl` user-photo string is separate and out of scope).

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: document school/course/program media FK model"
```

---

## Self-Review

**Spec coverage:**
- New categories → Task 1. ✅
- `MediaDTO.publicUrl` → Task 2. ✅
- Entity FK columns (School/Course/Program) → Tasks 4/6/5. ✅
- Asymmetric DTO (input id / output MediaDTO) → Tasks 4/5/6. ✅
- Mappers resolve via `MediaService` → Tasks 4/5/6. ✅
- `MediaService.deleteById` (no principal) + replace/delete cleanup → Task 3 + Tasks 4/5/6. ✅
- `V16` migration, drop columns, no data migration → Task 7. ✅
- Test each rule + opposite (replace deletes old / same id does not; admin upload vs non-admin already enforced by `assertCanUpload`, covered by category config in Task 1 + existing media tests) → Tasks 1/3/4/5/6. ✅
- Docs update → Task 8. ✅

**Placeholder scan:** Integration-test steps for Program/Course (Tasks 5/6 Step 7) describe modeling on existing tests rather than pasting full method bodies, because those files' create/update helpers vary; the persisted-media helper and exact JSON-path assertions are given verbatim. All service/mapper/entity code is complete.

**Type consistency:** `logoMediaId`/`coverMediaId`/`photoMediaId` are `Integer` on entity, DTO input, and service throughout; `logo`/`cover`/`photo` are `MediaDTO` on DTO output; `MediaService.findById(Integer): Optional<Media>` and `deleteById(Integer): void` used consistently in all three mappers/services; `publicUrl` is `String`. Consistent.
