package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.Role;
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
        userDTO.setRole(null);
        ensureRole("ROLE_STUDENT");

        String json = objectMapper.writeValueAsString(userDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );
    }

    @Test
    void testThatUserCanBeCreatedSuccessfullyAndRecalled() throws Exception {
        UserDTO user = TestDataUtil.createUserDto();
        user.setRole(null);
        ensureRole("ROLE_STUDENT");

        String json = objectMapper.writeValueAsString(user);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
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
        userDTO.setRole(ensureRole("ROLE_STUDENT"));
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        // Create school
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        // Add to wishlist
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users/" + user.getId() + "/wishlist/" + school.getId())
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.wishlist[0].id").value(school.getId())
        );

        // Remove from wishlist
        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/users/" + user.getId() + "/wishlist/" + school.getId())
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.wishlist.length()").value(0)
        );
    }

    @Test
    void testThatUserPatchUpdatesNewFields() throws Exception {
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(ensureRole("ROLE_STUDENT"));
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
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.firstname").value("Renamed")
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.qualification").value("Data Science")
        );
    }

    @Test
    void testThatPublicRegistrationAssignsStudentRoleEvenWhenAdminRoleIsRequested() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        Role adminRole = ensureRole("ROLE_ADMIN");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(adminRole);

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.role.id").value(studentRole.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.role.name").value("ROLE_STUDENT")
        );
    }

    @Test
    void testThatStudentCannotPatchOwnRole() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        Role adminRole = ensureRole("ROLE_ADMIN");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        com.bowe.meetstudent.dto.AdminUpdateUserRoleRequest updates =
                com.bowe.meetstudent.dto.AdminUpdateUserRoleRequest.builder().roleId(adminRole.getId()).build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );

        UserEntity unchangedUser = userService.getUserById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(studentRole.getId(), unchangedUser.getRole().getId());
    }

    @Test
    void testThatRoleFieldInProfilePatchIsIgnored() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        Role adminRole = ensureRole("ROLE_ADMIN");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        UserDTO updates = UserDTO.builder().firstname("Renamed").role(adminRole).build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.firstname").value("Renamed")
        );

        UserEntity unchangedUser = userService.getUserById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(studentRole.getId(), unchangedUser.getRole().getId());
    }

    @Test
    void testThatAdminCanPatchUserRole() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        Role expertRole = ensureRole("ROLE_EXPERT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        com.bowe.meetstudent.dto.AdminUpdateUserRoleRequest updates =
                com.bowe.meetstudent.dto.AdminUpdateUserRoleRequest.builder().roleId(expertRole.getId()).build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId() + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser(99, "ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.role.id").value(expertRole.getId())
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.role.name").value("ROLE_EXPERT")
        );
    }

    @Test
    void testThatRegistrationRejectsMismatchedPasswordConfirmation() throws Exception {
        ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(null);
        userDTO.setConfirmedPassword("something-else");

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        );
    }

    @Test
    void testThatRegistrationRejectsDuplicateEmail() throws Exception {
        ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(null);

        String json = objectMapper.writeValueAsString(userDTO);
        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        );

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        );
    }

    @Test
    void testThatUserCannotPatchAnotherUsersProfile() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO ownerDto = TestDataUtil.createUserDto();
        ownerDto.setRole(studentRole);
        UserEntity owner = userService.saveUser(userMapper.toEntity(ownerDto), passwordEncoder);

        UserDTO updates = UserDTO.builder().firstname("Hacked").build();

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates))
                        .with(TestDataUtil.mockUser(owner.getId() + 1, "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );

        UserEntity unchanged = userService.getUserById(owner.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("Hacked", unchanged.getFirstname());
    }

    @Test
    void testThatUserCannotManageAnotherUsersWishlist() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO ownerDto = TestDataUtil.createUserDto();
        ownerDto.setRole(studentRole);
        UserEntity owner = userService.saveUser(userMapper.toEntity(ownerDto), passwordEncoder);
        School school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users/" + owner.getId() + "/wishlist/" + school.getId())
                        .with(TestDataUtil.mockUser(owner.getId() + 1, "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isForbidden()
        );
    }

    private Role ensureRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
    }
}
