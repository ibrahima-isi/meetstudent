package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramDTO;
import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.mappers.implementations.AccreditationMapper;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.AccreditationService;
import com.bowe.meetstudent.services.ProgramAccreditationService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.services.SchoolService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ProgramControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProgramService programService;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private ProgramMapper programMapper;

    @Autowired
    private ProgramAccreditationService programAccreditationService;

    @Autowired
    private AccreditationService accreditationService;

    @Autowired
    private AccreditationMapper accreditationMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.bowe.meetstudent.repositories.MediaRepository mediaRepository;

    private com.bowe.meetstudent.entities.Media persistedProgramPhoto() {
        var media = com.bowe.meetstudent.entities.Media.builder()
                .storageKey("public/prog-it.png")
                .originalFilename("p.png").contentType("image/png").sizeBytes(10L)
                .category(com.bowe.meetstudent.entities.enums.MediaCategory.PROGRAM_PHOTO)
                .visibility(com.bowe.meetstudent.entities.enums.MediaVisibility.PUBLIC)
                .build();
        return mediaRepository.save(media);
    }

    @Test
    void duplicateProgramCodeReturnsConflictOnCreateAndPatch() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"First\",\"code\":\"P105A\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        String secondBody = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"code\":\"P105B\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int secondId = objectMapper.readTree(secondBody).get("id").asInt();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Twin\",\"code\":\"P105A\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/programs/" + secondId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"P105A\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + secondId)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("P105B"));
    }

    @Test
    void nonexistentSchoolIdReturnsUsefulFieldError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Programme\",\"code\":\"P105Y\",\"schoolId\":987654}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.schoolId").isNotEmpty());
    }

    @Test
    void patchingProgramWithUnknownSchoolReturnsFieldErrorAndKeepsAssociation() throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Programme\",\"code\":\"P105Z\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int id = objectMapper.readTree(response).get("id").asInt();
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/programs/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":987654}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.schoolId").isNotEmpty());
    }

    @Test
    void deleteProgramReturns200WithDeletedDto() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/programs/" + program.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(program.getId()));

        org.junit.jupiter.api.Assertions.assertTrue(programService.findById(program.getId()).isEmpty());
    }

    @Test
    void putProgramWithPhotoMediaIdResolvesPhotoWithPublicUrl() throws Exception {
        Program saved = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var media = persistedProgramPhoto();

        ProgramDTO body = TestDataUtil.createProgramDto();
        body.setPhotoMediaId(media.getId());
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/programs/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.id").value(media.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.photo.publicUrl").value("/uploads/public/prog-it.png"));
    }

    @Test
    void testThatCreateProgramReturnsHttpStatus201Created() throws Exception {
        ProgramDTO programDTO = TestDataUtil.createProgramDto();

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatProgramCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        ProgramDTO programDTO = TestDataUtil.createProgramDto();

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(programDTO.getName())
        );
    }

    @Test
    void testThatCreateProgramWithSchoolReturnsHttpStatus201() throws Exception {
        School school = new School();
        school.setName("Test School");
        school.setCode("TSCH1");
        school = schoolService.save(school);

        ProgramDTO programDTO = TestDataUtil.createProgramDto();
        programDTO.setSchoolId(school.getId());

        String json = objectMapper.writeValueAsString(programDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value(programDTO.getName())
        );
    }

    @Test
    void programReadsExposeTheSchoolNameAndCodeAndNullWhenDetached() throws Exception {
        School school = new School();
        school.setName("Ecole Alpha");
        school.setCode("ALPH");
        school = schoolService.save(school);

        ProgramDTO attached = TestDataUtil.createProgramDto();
        attached.setName("Zeta Attached");
        attached.setCode("ZATT");
        attached.setSchoolId(school.getId());
        ProgramDTO detached = TestDataUtil.createProgramDto();
        detached.setName("Zeta Detached");
        detached.setCode("ZDET");
        detached.setSchoolId(null);
        programService.save(programMapper.toEntity(attached));
        programService.save(programMapper.toEntity(detached));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Zeta")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].name").value("Zeta Attached"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].schoolId").value(school.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].schoolName").value("Ecole Alpha"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].schoolCode").value("ALPH"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].name").value("Zeta Detached"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].schoolId").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].schoolName").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].schoolCode").doesNotExist());
    }

    @Test
    void nameSearchWithoutSortPagesByNameThenId() throws Exception {
        for (var entry : new String[][] {{"Srch Zulu", "SZ1"}, {"Srch Alpha", "SA1"}, {"Srch Alpha", "SA2"}, {"Srch Mike", "SM1"}}) {
            ProgramDTO dto = TestDataUtil.createProgramDto();
            dto.setName(entry[0]);
            dto.setCode(entry[1]);
            dto.setSchoolId(null);
            programService.save(programMapper.toEntity(dto));
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Srch")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SA1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SA2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[3].code").value("SZ1"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Srch?size=2&page=1")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SZ1"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Srch?sort=name,desc")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
    }

    @Test
    void nameSearchKeepsTheDefaultPageSizeAndRejectsAnUnknownSort() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Srch")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/name/Srch?sort=doesNotExist,asc")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testThatGetAllProgramsReturnsHttpStatus200() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        );
    }

    @Test
    void testThatGetAllProgramsReturnsAListOfPrograms() throws Exception {
        ProgramDTO programDTO0 = TestDataUtil.createProgramDto();
        ProgramDTO programDTO1 = TestDataUtil.createProgramDto();
        programDTO1.setCode("PRG01"); // avoid unique constraint violation if faker duplicates

        this.programService.save(this.programMapper.toEntity(programDTO0));
        this.programService.save(this.programMapper.toEntity(programDTO1));

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs")
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
    void testThatLinkAccreditationReturnsHttpStatus201() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .param("startsAt", "2020")
                        .param("endsAt", "2025")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.startsAt").value(2020)
        );
    }

    @Test
    void linkingTheSameAccreditationTwiceReturnsConflict() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        String url = "/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId()
                + "?startsAt=2020&endsAt=2025";
        mockMvc.perform(MockMvcRequestBuilders.post(url).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        mockMvc.perform(MockMvcRequestBuilders.post(url).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void testThatGetProgramAccreditationReturnsHttpStatus200() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.startsAt").value(2020)
        );
    }

    @Test
    void testThatUpdateProgramAccreditationReturnsHttpStatus200() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .param("startsAt", "2021")
                        .param("endsAt", "2026")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.startsAt").value(2021)
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.endsAt").value(2026)
        );
    }

    @Test
    void testThatGetProgramAccreditationsReturnsList() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId() + "/accreditations")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].startsAt").value(2020)
        );
    }

    @Test
    void testThatUnlinkAccreditationReturnsHttpStatus204() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation acc = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        
        programAccreditationService.addAccreditationToProgram(program.getId(), acc.getId(), 2020, 2025);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/programs/" + program.getId() + "/accreditations/" + acc.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }

    @Test
    void unlinkRemovesOnlyTargetAssociationAndPreservesSharedAccreditation() throws Exception {
        Program first = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        ProgramDTO otherDto = TestDataUtil.createProgramDto();
        otherDto.setCode("P106B");
        Program other = programService.save(programMapper.toEntity(otherDto));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        programAccreditationService.addAccreditationToProgram(first.getId(), accreditation.getId(), 2020, 2025);
        programAccreditationService.addAccreditationToProgram(other.getId(), accreditation.getId(), 2021, 2026);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/programs/" + first.getId()
                        + "/accreditations/" + accreditation.getId()).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + first.getId()
                        + "/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + other.getId()
                        + "/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.startsAt").value(2021));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void unlinkMissingAssociationReturnsNotFoundWithoutChangingAnotherLink() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        int missingProgramId = program.getId() + 1000;
        programAccreditationService.addAccreditationToProgram(program.getId(), accreditation.getId(), 2020, 2025);

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/programs/" + missingProgramId
                        + "/accreditations/" + accreditation.getId()).with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId()
                        + "/accreditations/" + accreditation.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void studentCannotUnlinkAnAccreditation() throws Exception {
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        Accreditation accreditation = accreditationService.save(accreditationMapper.toEntity(TestDataUtil.createAccreditationDto()));
        programAccreditationService.addAccreditationToProgram(program.getId(), accreditation.getId(), 2020, 2025);

        String url = "/api/v1/programs/" + program.getId() + "/accreditations/" + accreditation.getId();
        mockMvc.perform(MockMvcRequestBuilders.delete(url).with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get(url))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    private int saveExpert() {
        var role = roleService.findRoleByName("ROLE_EXPERT")
                .orElseGet(() -> roleService.createRole(Role.builder().name("ROLE_EXPERT").build()));
        return userService.saveUser(UserEntity.builder().firstname("Rate").lastname("Expert")
                .email("rate.expert@example.com").password("password").role(role).build(), passwordEncoder).getId();
    }

    private int saveProgram(String name, String code) {
        ProgramDTO dto = TestDataUtil.createProgramDto();
        dto.setName(name);
        dto.setCode(code);
        dto.setSchoolId(null);
        return programService.save(programMapper.toEntity(dto)).getId();
    }

    private int saveRatedProgram(String name, String code, double note, int expertId) throws Exception {
        var id = saveProgram(name, code);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": " + note + ", \"programId\": " + id + ", \"userId\": " + expertId + "}")
                        .with(TestDataUtil.mockUser(expertId, "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isCreated());
        return id;
    }

    @Test
    void listWithoutSortPagesByNameThenIdAndAnExplicitSortWins() throws Exception {
        for (var entry : new String[][] {{"Srch Zulu", "SZ1"}, {"Srch Alpha", "SA1"}, {"Srch Alpha", "SA2"}, {"Srch Mike", "SM1"}}) {
            saveProgram(entry[0], entry[1]);
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SA1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SA2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[3].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?size=2&page=1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SM1"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].code").value("SZ1"));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].code").value("SZ1"));
    }

    @Test
    void listKeepsTheDefaultPageSizeAndRejectsAnUnknownSort() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?sort=doesNotExist,asc"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // Rate first, then name/id as tie-break. Unrated programs are not asserted: Hibernate
    // drops NULLS LAST on H2, whose test URL sorts nulls high.
    @Test
    void sortRateOrdersByRateThenNameAndIdWithRealRatings() throws Exception {
        var expertId = saveExpert();
        var tieBravo = saveRatedProgram("Tie Bravo", "TB001", 4.0, expertId);
        var tieAlpha = saveRatedProgram("Tie Alpha", "TA001", 4.0, expertId);
        var best = saveRatedProgram("Rated Zulu", "RZ001", 5.0, expertId);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?sortRate=most"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieBravo));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?sortRate=less"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(tieAlpha))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(best));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs?sortRate=most&sort=name,desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(best))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[1].id").value(tieBravo))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[2].id").value(tieAlpha));
    }
}
