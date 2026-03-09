package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.UserEntity;
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
        Role role = roleService.findRoleByName(roleName).orElseGet(() -> 
            roleService.createRole(Role.builder().name(roleName).build())
        );
        testUser = userService.saveUser(UserEntity.builder()
                .firstname("Test")
                .lastname("User")
                .email(roleName.toLowerCase() + "@example.com")
                .password("password")
                .role(role)
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
                        .with(TestDataUtil.mockUser("ROLE_EXPERT"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
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
                        .with(TestDataUtil.mockUser("ROLE_EXPERT"))
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
                        .with(TestDataUtil.mockUser("ROLE_EXPERT"))
        ).andReturn().getResponse().getContentAsString();
        
        ProgramRateDTO saved = objectMapper.readValue(response, ProgramRateDTO.class);

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/program-rates/" + saved.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }
}
