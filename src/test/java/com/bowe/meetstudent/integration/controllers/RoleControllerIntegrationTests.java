package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.RoleDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class RoleControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void testThatAdminCanCreateRole() throws Exception {
        RoleDTO roleDTO = RoleDTO.builder()
                .name("ROLE_MANAGER")
                .description("Manager")
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleDTO))
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("ROLE_MANAGER")
        );
    }

    @Test
    void testThatStudentCannotCreateRole() throws Exception {
        RoleDTO roleDTO = RoleDTO.builder().name("ROLE_MANAGER").build();

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleDTO))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatAdminCanListRoles() throws Exception {
        roleRepository.save(Role.builder().name("ROLE_MANAGER").description("Manager").build());

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/roles")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$[0].name").value("ROLE_MANAGER")
        );
    }

    @Test
    void testThatStudentCannotListRoles() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/roles")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatAdminCanUpdateRole() throws Exception {
        Role role = roleRepository.save(Role.builder().name("ROLE_OLD").description("Old").build());
        RoleDTO updates = RoleDTO.builder().name("ROLE_NEW").description("New").build();

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/roles/" + role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("ROLE_NEW")
        );
    }

    @Test
    void testThatStudentCannotUpdateRole() throws Exception {
        Role role = roleRepository.save(Role.builder().name("ROLE_OLD").description("Old").build());
        RoleDTO updates = RoleDTO.builder().name("ROLE_NEW").description("New").build();

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/roles/" + role.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    @Test
    void testThatAdminCanDeleteRole() throws Exception {
        Role role = roleRepository.save(Role.builder().name("ROLE_TEMP").description("Temporary").build());

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/roles/" + role.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );
    }

    @Test
    void testThatStudentCannotDeleteRole() throws Exception {
        Role role = roleRepository.save(Role.builder().name("ROLE_TEMP").description("Temporary").build());

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/roles/" + role.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }
}
