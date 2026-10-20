package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.CourseDTO;
import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.services.AccreditationService;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.services.ProgramAccreditationService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private ProgramService programService;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccreditationService accreditationService;

    @Autowired
    private AccreditationMapper accreditationMapper;

    @Autowired
    private ProgramAccreditationService programAccreditationService;

    @Autowired
    private com.bowe.meetstudent.repositories.MediaRepository mediaRepository;

    private com.bowe.meetstudent.entities.Media persistedCoursePhoto() {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/course-it.png")
                .originalFilename("c.png").contentType("image/png").sizeBytes(10L)
                .category(com.bowe.meetstudent.entities.enums.MediaCategory.COURSE_PHOTO)
                .visibility(com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC)
                .build();
        return mediaRepository.save(media);
    }

    @Test
    void unlinkCourseAccreditationPreservesSharedAccreditationAndOtherLinks() throws Exception {
        var first = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        CourseDTO secondDto = TestDataUtil.createCourseDto();
        secondDto.setCode("C106B");
        var second = courseService.save(courseMapper.toEntity(secondDto));
        Program program = new Program();
        program.setName("Shared program");
        program.setCode("P106C");
        program = programService.save(program);
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        programAccreditationService.addAccreditationToProgram(program.getId(), accreditation.getId(), 2020, 2025);

        String firstUrl = "/api/v1/courses/" + first.getId() + "/accreditations/" + accreditation.getId();
        String secondUrl = "/api/v1/courses/" + second.getId() + "/accreditations/" + accreditation.getId();
        mockMvc.perform(MockMvcRequestBuilders.post(firstUrl)
                        .param("startsAt", "2021").param("endsAt", "2026")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.startsAt").value(2021));
        mockMvc.perform(MockMvcRequestBuilders.post(secondUrl)
                        .param("startsAt", "2022").param("endsAt", "2027")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + first.getId() + "/accreditations"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].accreditationId").value(accreditation.getId()));

        mockMvc.perform(MockMvcRequestBuilders.delete(firstUrl).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        mockMvc.perform(MockMvcRequestBuilders.get(firstUrl))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.get(secondUrl))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.startsAt").value(2022));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId()
                        + "/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void courseAccreditationMissingLinkAndStudentMutationKeepData() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        String url = "/api/v1/courses/" + course.getId() + "/accreditations/" + accreditation.getId();
        mockMvc.perform(MockMvcRequestBuilders.delete(url).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .param("startsAt", "2021").param("endsAt", "2026")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void courseAccreditationRejectsDuplicateAndMissingResources() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        String url = "/api/v1/courses/" + course.getId() + "/accreditations/" + accreditation.getId();
        mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .param("startsAt", "2020").param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .param("startsAt", "2020").param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses/987654/accreditations/" + accreditation.getId())
                        .param("startsAt", "2020").param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses/" + course.getId() + "/accreditations/987654")
                        .param("startsAt", "2020").param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void accreditationLinkedOnlyToCourseCannotBeDeleted() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        String url = "/api/v1/courses/" + course.getId() + "/accreditations/" + accreditation.getId();
        mockMvc.perform(MockMvcRequestBuilders.post(url)
                        .param("startsAt", "2020").param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/accreditations/" + accreditation.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.get(url))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void courseDescriptionIsCreatedReadPatchedAndKeptWhenOmitted() throws Exception {
        String created = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algo\",\"code\":\"D02A\",\"description\":\"Introduction aux algorithmes\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Introduction aux algorithmes"))
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(created).get("id").asInt();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Introduction aux algorithmes"));

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Algo 2\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Introduction aux algorithmes"));

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/courses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Nouvelle description\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Nouvelle description"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Algo 2"));
    }

    @Test
    void courseWithoutDescriptionReadsNull() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + course.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value(Matchers.nullValue()));
    }

    @Test
    void courseDescriptionOver1000CharactersIsRejectedWithFieldError() throws Exception {
        String tooLong = "x".repeat(1001);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Long\",\"description\":\"" + tooLong + "\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").isNotEmpty());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"description\":\"" + "x".repeat(1000) + "\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void blankDescriptionClearsOnPatchAndPutAndIsNullOnCreate() throws Exception {
        String created = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blanc\",\"description\":\"   \"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value(Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(created).get("id").asInt();
        for (String method : new String[]{"patch", "put"}) {
            mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":\"  Texte  \"}")
                            .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Texte"));
            var builder = method.equals("patch")
                    ? MockMvcRequestBuilders.patch("/api/v1/courses/" + id)
                    : MockMvcRequestBuilders.put("/api/v1/courses/" + id);
            mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":\"\"}")
                            .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.description").value(Matchers.nullValue()));
        }
    }

    @Test
    void courseDescriptionWithControlCharacterIsRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nul\",\"description\":\"a\\u0000b\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").isNotEmpty());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nl\",\"description\":\"a\\nb\\tc\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void courseDescriptionWriteIsAdminOnly() throws Exception {
        var course = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        String body = "{\"description\":\"Interdit\"}";
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + course.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void duplicateCourseCodeReturnsConflict() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"code\":\"C105A\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"code\":\"C105A\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
    }

    @Test
    void unknownProgramIdOnCreateReturnsFieldError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cours\",\"code\":\"C105B\",\"programId\":987654}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.programId").isNotEmpty());
    }

    @Test
    void unknownProgramIdOnPatchReturnsFieldError() throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cours\",\"code\":\"C105C\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(response).get("id").asInt();
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/courses/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":987654}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.programId").isNotEmpty());
    }

    @Test
    void deleteCourseReturns200WithDeletedDto() throws Exception {
        com.bowe.meetstudent.entities.Course course =
                courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/courses/" + course.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(course.getId()));

        org.junit.jupiter.api.Assertions.assertTrue(courseService.findById(course.getId()).isEmpty());
    }

    @Test
    void putCourseWithPhotoMediaIdResolvesPhotoWithPublicUrl() throws Exception {
        com.bowe.meetstudent.entities.Course saved =
                courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto()));
        var media = persistedCoursePhoto();

        CourseDTO body = TestDataUtil.createCourseDto();
        body.setPhotoMediaId(media.getId());
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/courses/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.id").value(media.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.publicUrl").value("/uploads/public/course-it.png"));
    }

    @Test
    void testThatCreateCourseReturnsHttpStatus201Created() throws Exception {
        CourseDTO courseDTO = TestDataUtil.createCourseDto();

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatCourseCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        CourseDTO courseDTO = TestDataUtil.createCourseDto();

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(courseDTO.getName())
        );
    }

    @Test
    void testThatCreateCourseWithProgramReturnsHttpStatus201() throws Exception {
        Program program = new Program();
        program.setName("Test Program");
        program.setCode("TPRG1");
        program = programService.save(program);

        CourseDTO courseDTO = TestDataUtil.createCourseDto();
        courseDTO.setProgramId(program.getId());

        String json = objectMapper.writeValueAsString(courseDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(courseDTO.getName())
        );
    }

    @Test
    void testThatGetAllCoursesReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllCoursesReturnsAListOfCourses() throws Exception {
        CourseDTO courseDTO0 = TestDataUtil.createCourseDto();
        CourseDTO courseDTO1 = TestDataUtil.createCourseDto();
        courseDTO1.setCode("CRS01");

        this.courseService.save(this.courseMapper.toEntity(courseDTO0));
        this.courseService.save(this.courseMapper.toEntity(courseDTO1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").isNumber()
        );
    }

    @Test
    void testThatGetCoursesSortedByRateReturnsCorrectOrder() throws Exception {
        CourseDTO c1 = TestDataUtil.createCourseDto();
        c1.setName("Best Course");
        c1.setCode("BC001");
        com.bowe.meetstudent.entities.Course course1 = courseService.save(courseMapper.toEntity(c1));

        CourseDTO c2 = TestDataUtil.createCourseDto();
        c2.setName("Average Course");
        c2.setCode("AC001");
        com.bowe.meetstudent.entities.Course course2 = courseService.save(courseMapper.toEntity(c2));
        
        // Rate course1 with 5.0, course2 with 3.0 (only experts rate courses, and the user must exist)
        var expertId = saveExpert();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 5.0, \"courseId\": " + course1.getId() + ", \"userId\": " + expertId + "}")
                .with(TestDataUtil.mockUser(expertId, "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": 3.0, \"courseId\": " + course2.getId() + ", \"userId\": " + expertId + "}")
                .with(TestDataUtil.mockUser(expertId, "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/courses?sortRate=most")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].id").value(course1.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[1].id").value(course2.getId())
        );
    }

    private void saveCourse(String name, String code) {
        CourseDTO dto = TestDataUtil.createCourseDto();
        dto.setName(name);
        dto.setCode(code);
        courseService.save(courseMapper.toEntity(dto));
    }

    @Test
    void anonymousCanListAndSearchCoursesAndWritesAreRefused() throws Exception {
        saveCourse("Anon Course", "ANC01");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/name/Anon"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].name").value("Anon Course"));

        var body = "{\"name\":\"Nope\",\"code\":\"NOPE1\"}";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/courses/1")
                        .with(TestDataUtil.mockUser("ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void courseReadsExposeTheProgramIdAndNameAndNullWhenDetached() throws Exception {
        Program program = new Program();
        program.setName("Programme Alpha");
        program.setCode("PALPH");
        program = programService.save(program);

        CourseDTO attached = TestDataUtil.createCourseDto();
        attached.setName("Zeta Attached");
        attached.setCode("ZATT1");
        var attachedEntity = courseMapper.toEntity(attached);
        attachedEntity.setProgram(program);
        courseService.save(attachedEntity);
        saveCourse("Zeta Detached", "ZDET1");

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/name/Zeta"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].name").value("Zeta Attached"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].programId").value(program.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].programName").value("Programme Alpha"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].name").value("Zeta Detached"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].programId").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].programName").doesNotExist());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].programName").value("Programme Alpha"));
    }

    @Test
    void nameSearchAndListWithoutSortPageByNameThenId() throws Exception {
        for (var entry : new String[][] {{"Srch Zulu", "SZ1"}, {"Srch Alpha", "SA1"}, {"Srch Alpha", "SA2"}, {"Srch Mike", "SM1"}}) {
            saveCourse(entry[0], entry[1]);
        }

        for (var path : new String[] {"/api/v1/courses/name/Srch", "/api/v1/courses"}) {
            mockMvc.perform(MockMvcRequestBuilders.get(path))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SA1"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SA2"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].code").value("SM1"))
                    .andExpect(MockMvcResultMatchers.jsonPath("$.content[3].code").value("SZ1"));
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/name/Srch?size=2&page=1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SZ1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(4));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/name/Srch?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
    }

    private int saveExpert() {
        var role = roleService.findRoleByName("ROLE_EXPERT")
                .orElseGet(() -> roleService.createRole(Role.builder().name("ROLE_EXPERT").build()));
        return userService.saveUser(UserEntity.builder().firstname("Rate").lastname("Expert")
                .email("rate.expert@example.com").password("password").role(role).build(), passwordEncoder).getId();
    }

    private int saveRatedCourse(String name, String code, double note, int expertId) throws Exception {
        CourseDTO dto = TestDataUtil.createCourseDto();
        dto.setName(name);
        dto.setCode(code);
        var id = courseService.save(courseMapper.toEntity(dto)).getId();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/course-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": " + note + ", \"courseId\": " + id + ", \"userId\": " + expertId + "}")
                        .with(TestDataUtil.mockUser(expertId, "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        return id;
    }

    // Approved rule: rate first, then name/id, so equal rates page stably too. Unrated courses
    // are not asserted here: Hibernate drops NULLS LAST on H2, whose test URL sorts nulls high.
    @Test
    void sortRateOrdersByRateThenNameAndIdAndKeepsTheRateFirstOverAnExplicitSort() throws Exception {
        var expertId = saveExpert();
        var tieBravo = saveRatedCourse("Tie Bravo", "TB001", 4.0, expertId);
        var tieAlpha = saveRatedCourse("Tie Alpha", "TA001", 4.0, expertId);
        var best = saveRatedCourse("Rated Zulu", "RZ001", 5.0, expertId);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses?sortRate=most"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieBravo));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses?sortRate=less"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(best));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses?sortRate=most&sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieAlpha));
    }

    @Test
    void listAndSearchKeepTheDefaultPageSizeAndRejectAnUnknownSort() throws Exception {
        for (var path : new String[] {"/api/v1/courses", "/api/v1/courses/name/Srch"}) {
            mockMvc.perform(MockMvcRequestBuilders.get(path))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20));
            mockMvc.perform(MockMvcRequestBuilders.get(path + "?sort=doesNotExist,asc"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest());
        }
    }
}
