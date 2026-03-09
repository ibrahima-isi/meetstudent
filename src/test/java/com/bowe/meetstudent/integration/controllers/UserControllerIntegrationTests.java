package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.Mapper;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
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

import java.util.List;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private SchoolMapper schoolMapper;

    @Autowired
    private Mapper<UserEntity, UserDTO> userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testThatCreateUserReturnStatusCode201Created() throws Exception {
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.getRole().setId(null);
        var savedRole = roleRepository.save(userDTO.getRole());
        userDTO.setRole(savedRole);

        String json = objectMapper.writeValueAsString(userDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatUserCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        UserDTO user = TestDataUtil.createUserDto();
        user.getRole().setId(null);
        var savedRole = roleRepository.save(user.getRole());
        user.setRole(savedRole);

        String json = objectMapper.writeValueAsString(user);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.id").isNumber()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.firstname").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.lastname").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.email").isNotEmpty()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.password").doesNotExist()
        );
    }

    @Test
    void testThatWishlistCanBeManaged() throws Exception {
        // Create user
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.getRole().setId(null);
        var savedRole = roleRepository.save(userDTO.getRole());
        userDTO.setRole(savedRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        // Create school
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        // Add to wishlist
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users/" + user.getId() + "/wishlist/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.wishlist[0].id").value(school.getId())
        );

        // Remove from wishlist
        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/users/" + user.getId() + "/wishlist/" + school.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.wishlist.length()").value(0)
        );
    }

    @Test
    void testThatUserPatchUpdatesNewFields() throws Exception {
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.getRole().setId(null);
        var savedRole = roleRepository.save(userDTO.getRole());
        userDTO.setRole(savedRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        UserDTO updates = UserDTO.builder()
                .presentationVideoUrl("videos/test.mp4")
                .certificates(List.of("certs/c1.pdf"))
                .build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.presentationVideoUrl").value("videos/test.mp4")
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.certificates[0]").value("certs/c1.pdf")
        );
    }
}
