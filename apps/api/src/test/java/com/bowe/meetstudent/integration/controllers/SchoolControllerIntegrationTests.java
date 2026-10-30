package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.mappers.implementations.TagMapper;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.TagService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ExtendWith(ResetStateExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchoolControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private TagService tagService;

    @Autowired
    private SchoolMapper schoolMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.bowe.meetstudent.repositories.MediaRepository mediaRepository;

    private com.bowe.meetstudent.entities.Media persistedLogo() {
        return persisted(com.bowe.meetstudent.entities.enums.MediaCategory.SCHOOL_LOGO);
    }

    private com.bowe.meetstudent.entities.Media persisted(com.bowe.meetstudent.entities.enums.MediaCategory category) {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/logo-it.png")
                .originalFilename("logo.png").contentType("image/png").sizeBytes(10L)
                .category(category)
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
    void getSchoolFullyMapsNestedProgramAndCoursePhotos() throws Exception {
        var programPhoto = persisted(com.bowe.meetstudent.entities.enums.MediaCategory.PROGRAM_PHOTO);
        var coursePhoto = persisted(com.bowe.meetstudent.entities.enums.MediaCategory.COURSE_PHOTO);
        var courseDto = TestDataUtil.createCourseDto();
        courseDto.setPhotoMediaId(coursePhoto.getId());
        var programDto = TestDataUtil.createProgramDto();
        programDto.setPhotoMediaId(programPhoto.getId());
        programDto.setCourses(List.of(courseDto));
        var schoolDto = TestDataUtil.createSchoolDto();
        schoolDto.setPrograms(List.of(programDto));
        School school = schoolService.save(schoolMapper.toEntity(schoolDto));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools/" + school.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.programs[0].photo.id").value(programPhoto.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.programs[0].photo.publicUrl").value("/uploads/public/logo-it.png"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.programs[0].photo.category").value("PROGRAM_PHOTO"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.programs[0].courses[0].photo.publicUrl").value("/uploads/public/logo-it.png"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.programs[0].courses[0].photo.category").value("COURSE_PHOTO"));
    }

    @Test
    void deleteSchoolReturns200WithDeletedDto() throws Exception {
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(school.getId()));

        org.junit.jupiter.api.Assertions.assertTrue(schoolService.getSchoolById(school.getId()).isEmpty());
    }

    @Test
    void deleteSchoolRemovesReferencedLogoMedia() {
        var media = persistedLogo();
        School saved = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        saved.setLogoMediaId(media.getId());
        School school = schoolService.save(saved);

        schoolService.delete(school.getId());

        org.junit.jupiter.api.Assertions.assertTrue(mediaRepository.findById(media.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(schoolService.getSchoolById(school.getId()).isEmpty());
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void addToWishlist(int userId, int schoolId) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/users/" + userId + "/wishlist/" + schoolId)
                        .with(TestDataUtil.mockUser(userId, "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
    }

    @Test
    void deleteSchoolPresentInAWishlistRemovesTheLinkAndSucceeds() throws Exception {
        int studentId = saveStudent();
        int doomed = saveSchool("Doomed school", "DOO");
        int kept = saveSchool("Kept school", "KEP");
        addToWishlist(studentId, doomed);
        addToWishlist(studentId, kept);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + doomed)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(doomed));

        org.junit.jupiter.api.Assertions.assertTrue(schoolService.getSchoolById(doomed).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("select count(*) from user_wishlist", Integer.class));
        org.junit.jupiter.api.Assertions.assertEquals(kept,
                jdbc.queryForObject("select school_id from user_wishlist where user_id = ?", Integer.class, studentId));
    }

    @Autowired
    private com.bowe.meetstudent.repositories.ProgramRepository programRepository;

    @Test
    void deleteSchoolWhoseProgramIsInAProgramWishlistRemovesTheLinkAndSucceeds() throws Exception {
        int studentId = saveStudent();
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        var program = programRepository.save(
                com.bowe.meetstudent.entities.Program.builder().name("Wished program").school(school).build());
        mockMvc.perform(MockMvcRequestBuilders.post(
                                "/api/v1/users/" + studentId + "/wishlist/programs/" + program.getId())
                        .with(TestDataUtil.mockUser(studentId, "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(schoolService.getSchoolById(school.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                jdbc.queryForObject("select count(*) from user_program_wishlist", Integer.class));
    }

    @Test
    void deleteSchoolInAWishlistKeepsOtherSchoolsAndRemovesLogoMedia() throws Exception {
        int studentId = saveStudent();
        var media = persistedLogo();
        School saved = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        saved.setLogoMediaId(media.getId());
        int schoolId = schoolService.save(saved).getId();
        addToWishlist(studentId, schoolId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + schoolId)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(mediaRepository.findById(media.getId()).isEmpty());
    }

    @Test
    void deleteSchoolAsStudentIsForbiddenAndKeepsTheSchoolAndItsWishlistLink() throws Exception {
        int studentId = saveStudent();
        int schoolId = saveSchool("Protected school", "PRO");
        addToWishlist(studentId, schoolId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/schools/" + schoolId)
                        .with(TestDataUtil.mockUser(studentId, "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());

        org.junit.jupiter.api.Assertions.assertTrue(schoolService.getSchoolById(schoolId).isPresent());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("select count(*) from user_wishlist", Integer.class));
    }

    @Test
    void testThatCreateSchoolReturnsHttpStatus201Created() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        String schoolJson = objectMapper.writeValueAsString(schoolDTO);

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(schoolJson)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatSchoolCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        SchoolDTO schoolDTO = TestDataUtil.createSchoolDto();
        School savedSchool = schoolService.save(schoolMapper.toEntity(schoolDTO));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/" + savedSchool.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").value(savedSchool.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(schoolDTO.getName())
        );
    }

    @Test
    void testThatGetAllSchoolsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    private int saveStudent() {
        var role = roleService.findRoleByName("ROLE_STUDENT")
                .orElseGet(() -> roleService.createRole(Role.builder().name("ROLE_STUDENT").build()));
        return userService.saveUser(UserEntity.builder().firstname("Rate").lastname("Student")
                .email("rate.student@example.com").password("password").role(role).build(), passwordEncoder).getId();
    }

    private int saveSchool(String name, String code) {
        SchoolDTO dto = TestDataUtil.createSchoolDto();
        dto.setName(name);
        dto.setCode(code);
        return schoolService.save(schoolMapper.toEntity(dto)).getId();
    }

    private int saveRatedSchool(String name, String code, double note, int studentId) throws Exception {
        var id = saveSchool(name, code);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/school-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": " + note + ", \"schoolId\": " + id + ", \"userId\": " + studentId + "}")
                        .with(TestDataUtil.mockUser(studentId, "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        return id;
    }

    @Test
    void listWithoutSortPagesByNameThenIdAndAnExplicitSortWins() throws Exception {
        for (var entry : new String[][] {{"Srch Zulu", "SZ1"}, {"Srch Alpha", "SA1"}, {"Srch Alpha", "SA2"}, {"Srch Mike", "SM1"}}) {
            saveSchool(entry[0], entry[1]);
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SA1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SA2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[3].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?size=2&page=1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
    }

    @Test
    void listKeepsTheDefaultPageSizeAndRejectsAnUnknownSort() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?sort=doesNotExist,asc"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // Rate first, then name/id as tie-break. Unrated schools are not asserted: Hibernate
    // drops NULLS LAST on H2, whose test URL sorts nulls high.
    @Test
    void sortRateOrdersByRateThenNameAndIdWithRealRatings() throws Exception {
        var studentId = saveStudent();
        var tieBravo = saveRatedSchool("Tie Bravo", "TB001", 4.0, studentId);
        var tieAlpha = saveRatedSchool("Tie Alpha", "TA001", 4.0, studentId);
        var best = saveRatedSchool("Rated Zulu", "RZ001", 5.0, studentId);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?sortRate=most"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieBravo));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?sortRate=less"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(best));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools?sortRate=most&sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieAlpha));
    }

    @Test
    void testThatSearchSchoolsByTagReturnsCorrectResults() throws Exception {
        Tag publicTag = tagService.findOrCreate("PUBLIC");
        Tag privateTag = tagService.findOrCreate("PRIVATE");

        School school1 = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        school1.setName("Public School");
        school1.setCode("PUB01");
        school1.setTags(List.of(publicTag));
        schoolService.save(school1);

        School school2 = schoolMapper.toEntity(TestDataUtil.createSchoolDto());
        school2.setName("Private School");
        school2.setCode("PRV01");
        school2.setTags(List.of(privateTag));
        schoolService.save(school2);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/schools/search?tag=PUBLIC")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.content[0].name").value("Public School")
        );
    }
}
