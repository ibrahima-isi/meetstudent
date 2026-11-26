package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.CourseRepository;
import com.bowe.meetstudent.repositories.ProgramRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.services.MediaWriteGate;
import com.bowe.meetstudent.support.ResetStateExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(ResetStateExtension.class)
class MediaWritePauseIntegrationTests {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', ' '};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0};

    @TempDir
    static Path mediaRoot;

    @DynamicPropertySource
    static void isolateMediaDirectories(DynamicPropertyRegistry properties) {
        properties.add("file.upload-dir", () -> mediaRoot.resolve("uploads").toString());
        properties.add("file.private-dir", () -> mediaRoot.resolve("private").toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MediaWriteGate mediaWriteGate;
    @Autowired
    SchoolRepository schoolRepository;
    @Autowired
    ProgramRepository programRepository;
    @Autowired
    CourseRepository courseRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void ensureWritesResumed() {
        mediaWriteGate.resume();
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "diploma.pdf", "application/pdf", PDF);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "logo.png", "image/png", PNG);
    }

    private JsonNode upload(int userId, String role, MockMultipartFile file, String category) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/media").file(file)
                        .param("category", category)
                        .with(TestDataUtil.mockUser(userId, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void maintenanceEndpointsEnforceAdminRoleAndValidateWaitSeconds() throws Exception {
        mockMvc.perform(get("/api/v1/admin/media/maintenance"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/media/maintenance")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/media/maintenance")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(false))
                .andExpect(jsonPath("$.storageBackend").value("disk"))
                .andExpect(jsonPath("$.inFlightWrites").value(0))
                .andExpect(jsonPath("$.pendingDeferredDeletions").value(0));

        mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                        .param("waitSeconds", "-1")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.waitSeconds").exists());
    }

    @Test
    void pausedUploadsReturn503WithRetryAfterWhileReadsAndAuthContinueWorking() throws Exception {
        var diploma = upload(7, "ROLE_STUDENT", pdf(), "DIPLOMA");
        var logo = upload(99, "ROLE_ADMIN", png(), "SCHOOL_LOGO");
        int diplomaId = diploma.get("id").asInt();
        String logoUrl = logo.get("publicUrl").asText();

        mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                        .param("waitSeconds", "5")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(true))
                .andExpect(jsonPath("$.drained").value(true));

        // Uploads return 503 + Retry-After, while auth/role guards still execute.
        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "DIPLOMA"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/media").file(png()).param("category", "SCHOOL_LOGO")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.status").value(503));

        // Public reads, private reads, and authorization checks remain functional.
        mockMvc.perform(get(logoUrl)).andExpect(status().isOk()).andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/v1/media/{id}", diplomaId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
        mockMvc.perform(get("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
        mockMvc.perform(get("/api/v1/auth/capabilities")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/media/maintenance/resume")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(false));

        mockMvc.perform(multipart("/api/v1/media").file(pdf()).param("category", "DIPLOMA")
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isCreated());
    }

    @Test
    void pausedMediaDeleteChecksOwnershipOrAdminBeforeReturning503() throws Exception {
        var diploma = upload(7, "ROLE_STUDENT", pdf(), "DIPLOMA");
        int diplomaId = diploma.get("id").asInt();

        mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                        .param("waitSeconds", "5")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(true));

        // Anonymous -> 401, non-owner (and non-owner on unknown id) -> 403 even while paused.
        mockMvc.perform(delete("/api/v1/media/{id}", diplomaId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/media/{id}", 999_999)
                        .with(TestDataUtil.mockUser(8, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        // Owner and admin pass authorization and hit the pause gate (503 + Retry-After).
        mockMvc.perform(delete("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(7, "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/media/{id}", 2_147_483_647)
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void pausedModeBlocksAllMediaAndReferenceMutationsWith503AndRetryAfterWhileKeepingReadsAndProfileEdits() throws Exception {
        Role studentRole = roleRepository.save(Role.builder().name("ROLE_STUDENT").build());
        UserEntity user = userRepository.save(UserEntity.builder()
                .firstname("Awa")
                .lastname("Diallo")
                .email("awa@example.com")
                .password(passwordEncoder.encode("SecretPass123!"))
                .role(studentRole)
                .build());
        School school = schoolRepository.save(School.builder().name("ESMT").code("ESMT1").build());
        Program program = programRepository.save(Program.builder().name("Master").code("M1").duration(2).school(school).build());
        Course course = courseRepository.save(Course.builder().name("Algo").code("ALG1").program(program).build());
        var diploma = upload(user.getId(), "ROLE_STUDENT", pdf(), "DIPLOMA");
        int diplomaId = diploma.get("id").asInt();

        mockMvc.perform(post("/api/v1/admin/media/maintenance/pause")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.writesPaused").value(true));

        // Media verification & deletion
        mockMvc.perform(patch("/api/v1/media/{id}/verification", diplomaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/media/{id}/verification", diplomaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/media/{id}", diplomaId)
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        // School / Program / Course mutations
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"code\":\"NEW1\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"code\":\"NEW1\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(put("/api/v1/schools/{id}", school.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ESMT2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(patch("/api/v1/schools/{id}", school.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ESMT2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/schools/{id}", school.getId())
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        mockMvc.perform(post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Prog\",\"code\":\"PRG2\",\"duration\":2}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(put("/api/v1/programs/{id}", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(patch("/api/v1/programs/{id}", program.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"M2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/programs/{id}", program.getId())
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        mockMvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"C2\",\"code\":\"C2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(put("/api/v1/courses/{id}", course.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algo2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(patch("/api/v1/courses/{id}", course.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algo2\"}")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
        mockMvc.perform(delete("/api/v1/courses/{id}", course.getId())
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        // Account deletion paused (ownership check still executes first)
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                        .with(TestDataUtil.mockUser(user.getId() + 1, "ROLE_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SecretPass123!\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

        // Catalogue reads and non-media profile updates stay operational.
        mockMvc.perform(get("/api/v1/schools/{id}", school.getId())).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Fatou\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstname").value("Fatou"));

        // Resume and verify account deletion & entity updates work again.
        mockMvc.perform(post("/api/v1/admin/media/maintenance/resume")
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/{id}", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"SecretPass123!\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT")))
                .andExpect(status().isNoContent());
    }
}
