package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.services.RoleService;
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
class ProgramRateControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProgramService programService;

    @Autowired
    private ProgramMapper programMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UserEntity testUser;

    private void setupUser(String roleName) {
        testUser = createUser(roleName, roleName.toLowerCase() + "@example.com");
    }

    private UserEntity createUser(String roleName, String email) {
        Role role = roleService.findRoleByName(roleName).orElseGet(() -> 
            roleService.createRole(Role.builder().name(roleName).build())
        );
        return userService.saveUser(UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email(email)
                .password("password")
                .role(role)
                .expertStatus("ROLE_EXPERT".equals(roleName) ? ExpertStatus.APPROVED : null)
                .build(), passwordEncoder);
    }

    @Test
    void testThatCreateProgramRateReturnsHttpStatus201() throws Exception {
        setupUser("ROLE_EXPERT");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        
        ProgramRateDTO rateDTO = ProgramRateDTO.builder()
                .note(4.5)
                .comment("Great program")
                .programId(program.getId())
                .userId(testUser.getId())
                .build();

        String json = objectMapper.writeValueAsString(rateDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId())
        );
    }

    @Test
    void testThatCreateProgramRateRejectsAnotherUserId() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity otherUser = createUser("ROLE_EXPERT", "other-expert@example.com");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));

        ProgramRateDTO rateDTO = ProgramRateDTO.builder()
                .note(4.5)
                .comment("Impersonation attempt")
                .programId(program.getId())
                .userId(otherUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatStudentCannotCreateProgramRate() throws Exception {
        setupUser("ROLE_STUDENT");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        ProgramRateDTO rateDTO = ProgramRateDTO.builder()
                .note(4.5)
                .programId(program.getId())
                .userId(testUser.getId())
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatGetProgramRateByIdReturnsHttpStatus200() throws Exception {
        setupUser("ROLE_EXPERT");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        ProgramRateDTO rateDTO = ProgramRateDTO.builder().note(4.0).programId(program.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();
        
        ProgramRateDTO saved = objectMapper.readValue(response, ProgramRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/program-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.note").value(4.0)
        );
    }

    @Test
    void testThatDeleteProgramRateReturnsHttpStatus204() throws Exception {
        setupUser("ROLE_EXPERT");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        ProgramRateDTO rateDTO = ProgramRateDTO.builder().note(4.0).programId(program.getId()).userId(testUser.getId()).build();
        
        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();
        
        ProgramRateDTO saved = objectMapper.readValue(response, ProgramRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/program-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }

    @Test
    void testThatExpertCannotDeleteProgramRate() throws Exception {
        setupUser("ROLE_EXPERT");
        Program program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        ProgramRateDTO rateDTO = ProgramRateDTO.builder().note(4.0).programId(program.getId()).userId(testUser.getId()).build();

        String response = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateDTO))
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();

        ProgramRateDTO saved = objectMapper.readValue(response, ProgramRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/program-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatRatingTheSameProgramTwiceUpdatesTheRatingInPlace() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var body = ProgramRateDTO.builder().note(2.0).comment("first").programId(target.getId()).userId(testUser.getId()).build();
        var first = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = objectMapper.readValue(first, ProgramRateDTO.class).getId();

        body.setNote(5.0);
        body.setComment("second");
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isOk())
         .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id))
         .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(5.0))
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("second"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates").with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1));
    }

    @Test
    void testThatTwoUsersCanEachRateTheSameProgram() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity other = createUser("ROLE_EXPERT", "second-rater@example.com");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        for (var user : new UserEntity[] {testUser, other}) {
            var body = ProgramRateDTO.builder().note(3.0).programId(target.getId()).userId(user.getId()).build();
            mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(user.getId(), "ROLE_EXPERT"))
            ).andExpect(MockMvcResultMatchers.status().isCreated());
        }
    }

    @Test
    void testThatPostingAnotherUsersRateIdCannotOverwriteThatRating() throws Exception {
        setupUser("ROLE_EXPERT");
        UserEntity other = createUser("ROLE_EXPERT", "thief@example.com");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var victimBody = ProgramRateDTO.builder().note(4.0).comment("victim").programId(target.getId()).userId(testUser.getId()).build();
        var created = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(victimBody)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        int victimId = objectMapper.readValue(created, ProgramRateDTO.class).getId();

        var attack = ProgramRateDTO.builder().id(victimId).note(1.0).comment("stolen").programId(target.getId()).userId(other.getId()).build();
        var attackResult = mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attack)).with(TestDataUtil.mockUser(other.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(victimId, objectMapper.readValue(attackResult, ProgramRateDTO.class).getId());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates/" + victimId).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(4.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("victim"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(testUser.getId()));
    }

    @Test
    void testThatARatingWithoutProgramIdIsRejectedWith400AndCreatesNoRow() throws Exception {
        setupUser("ROLE_EXPERT");
        var auth = TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT");
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(
                    MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":5}").with(auth)
            ).andExpect(MockMvcResultMatchers.status().isBadRequest())
             .andExpect(MockMvcResultMatchers.jsonPath("$.programId").value("Program ID is required"));
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates").with(auth))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0));
    }

    @Test
    void testThatACommentLongerThanTheColumnIsRejectedWith400() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var body = ProgramRateDTO.builder().note(3.0).comment("x".repeat(151)).programId(target.getId()).userId(testUser.getId()).build();
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)).with(TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT"))
        ).andExpect(MockMvcResultMatchers.status().isBadRequest())
         .andExpect(MockMvcResultMatchers.jsonPath("$.comment").exists());
    }

    @Test
    void testThatReRatingWithoutACommentKeepsItButAnEmptyOneClearsIt() throws Exception {
        setupUser("ROLE_EXPERT");
        var target = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var auth = TestDataUtil.mockUser(testUser.getId(), "ROLE_EXPERT");
        var body = ProgramRateDTO.builder().note(2.0).comment("keep me").programId(target.getId()).userId(testUser.getId()).build();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth)).andExpect(MockMvcResultMatchers.status().isCreated());

        body.setNote(4.0);
        body.setComment(null);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(4.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value("keep me"));

        body.setNote(5.0);
        body.setComment("");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)).with(auth))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(5.0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment").value(""));
    }
    @Test
    void reRatingChangesOnlyTheAuthorsProgramRatingAndRecomputesTheAverage() throws Exception {
        setupUser("ROLE_EXPERT");
        var other = createUser("ROLE_EXPERT", "independent-expert@example.com");
        var program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var anotherDto = TestDataUtil.createProgramDto();
        anotherDto.setCode("OTHER");
        var anotherProgram = programService.save(programMapper.toEntity(anotherDto));
        var first = postRating(testUser, "ROLE_EXPERT", program.getId(), 2.0, 201);
        var independent = postRating(other, "ROLE_EXPERT", program.getId(), 3.0, 201);
        var elsewhere = postRating(testUser, "ROLE_EXPERT", anotherProgram.getId(), 1.0, 201);

        var updated = postRating(testUser, "ROLE_EXPERT", program.getId(), 5.0, 200);
        org.junit.jupiter.api.Assertions.assertEquals(first.getId(), updated.getId());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates/program/" + program.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates/" + independent.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(other.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(3.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates/" + elsewhere.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").value(1.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(4.0));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + anotherProgram.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(1.0));
    }

    @Test
    void unauthorizedRolesAndAnonymousCannotCreateOrReplaceAProgramRating() throws Exception {
        setupUser("ROLE_EXPERT");
        var program = programService.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var existing = postRating(testUser, "ROLE_EXPERT", program.getId(), 4.0, 201);
        for (var role : new String[] {"ROLE_STUDENT", "ROLE_ADMIN", "ANONYMOUS"}) {
            var body = ProgramRateDTO.builder().id(existing.getId()).userId(testUser.getId())
                    .programId(program.getId()).note(1.0).build();
            var request = MockMvcRequestBuilders.post("/api/v1/program-rates")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
            if (!role.equals("ANONYMOUS")) request.with(TestDataUtil.mockUser(testUser.getId(), role));
            mockMvc.perform(request).andExpect(MockMvcResultMatchers.status().is(role.equals("ANONYMOUS") ? 401 : 403));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/program-rates/program/" + program.getId()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(existing.getId()))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].userId").value(testUser.getId()))
                    .andExpect(MockMvcResultMatchers.jsonPath("$[0].note").value(4.0));
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId()))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.averageRate").value(4.0));
        }
    }

    private ProgramRateDTO postRating(UserEntity user, String role, Integer programId, double note, int status) throws Exception {
        var body = ProgramRateDTO.builder().programId(programId).userId(user.getId()).note(note).build();
        var response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/program-rates")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))
                        .with(TestDataUtil.mockUser(user.getId(), role)))
                .andExpect(MockMvcResultMatchers.status().is(status)).andReturn().getResponse();
        return objectMapper.readValue(response.getContentAsString(), ProgramRateDTO.class);
    }

}
