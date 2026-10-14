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
    void testThatRegistrationRejectsPasswordShorterThanEightCharacters() throws Exception {
        ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(null);
        userDTO.setPassword("short12");
        userDTO.setConfirmedPassword("short12");

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDTO))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.password").value("Le mot de passe doit contenir au moins 8 caractères")
        );
    }

    @Test
    void testThatProfilePatchRejectsPasswordShorterThanEightCharacters() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short12\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.password").value("Le mot de passe doit contenir au moins 8 caractères")
        );
    }

    @Test
    void testThatProfilePutRejectsPasswordShorterThanEightCharacters() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        mockMvc.perform(
                MockMvcRequestBuilders.put("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short12\"}")
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isBadRequest()
        );
    }

    @Test
    void testThatProfilePatchAcceptsEightCharacterPassword() throws Exception {
        Role studentRole = ensureRole("ROLE_STUDENT");
        UserDTO userDTO = TestDataUtil.createUserDto();
        userDTO.setRole(studentRole);
        UserEntity user = userService.saveUser(userMapper.toEntity(userDTO), passwordEncoder);

        mockMvc.perform(
                MockMvcRequestBuilders.patch("/api/v1/users/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("password", "exactly8", "currentPassword", userDTO.getPassword())))
                        .with(TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
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

    @Test
    void selfDeletionRequiresCurrentPasswordAndLeavesAccountUsableWhenRejected() throws Exception {
        var dto = TestDataUtil.createUserDto();
        dto.setRole(ensureRole("ROLE_STUDENT"));
        var user = userService.saveUser(userMapper.toEntity(dto), passwordEncoder);
        var auth = TestDataUtil.mockUser(user.getId(), "ROLE_STUDENT");
        for (var body : List.of("", "{}", "{\"currentPassword\":\"wrong\"}")) {
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + user.getId())
                            .with(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.currentPassword").exists());
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + user.getId()).with(auth))
                    .andExpect(MockMvcResultMatchers.status().isOk());
        }
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + user.getId())
                        .with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", dto.getPassword()))))
                .andExpect(MockMvcResultMatchers.status().isNoContent())
                .andExpect(MockMvcResultMatchers.content().string(""));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + user.getId())
                        .with(TestDataUtil.mockUser(999, "ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void administratorNeedsPasswordForSelfButCanDeleteAnotherAccountWithoutIt() throws Exception {
        var dto = TestDataUtil.createUserDto();
        dto.setRole(ensureRole("ROLE_ADMIN"));
        var admin = userService.saveUser(userMapper.toEntity(dto), passwordEncoder);
        var auth = TestDataUtil.mockUser(admin.getId(), "ROLE_ADMIN");
        for (var body : List.of("", "{}", "{\"currentPassword\":\"wrong\"}")) {
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + admin.getId())
                            .with(auth).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.currentPassword").exists());
        }
        var target = userService.saveUser(UserEntity.builder().email("target104@example.com")
                .password("password104").role(ensureRole("ROLE_STUDENT")).build(), passwordEncoder);
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + target.getId()).with(auth))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + target.getId()).with(auth))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + admin.getId()).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", dto.getPassword()))))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    void deletionRejectsAnonymousAndOtherUsersBeforeAnyMutation() throws Exception {
        var dto = TestDataUtil.createUserDto();
        dto.setRole(ensureRole("ROLE_STUDENT"));
        var owner = userService.saveUser(userMapper.toEntity(dto), passwordEncoder);
        var path = "/api/v1/users/" + owner.getId();
        mockMvc.perform(MockMvcRequestBuilders.delete(path)).andExpect(MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.delete(path)
                        .with(TestDataUtil.mockUser(owner.getId() + 1, "ROLE_STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", dto.getPassword()))))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + owner.getId())
                        .with(TestDataUtil.mockUser(owner.getId(), "ROLE_STUDENT")))
                .andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.delete(path).with(TestDataUtil.mockUser(999, "ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Autowired private com.bowe.meetstudent.repositories.ProgramRepository programs;
    @Autowired private com.bowe.meetstudent.repositories.CourseRepository courses;
    @Autowired private com.bowe.meetstudent.services.UserTokenService actionTokens;

    @Test
    void deletionRemovesDependentRatingsWishlistMediaAndAllDevicesButPreservesOtherUsers() throws Exception {
        var dto = TestDataUtil.createUserDto();
        dto.setRole(ensureRole("ROLE_EXPERT"));
        var owner = userService.saveUser(userMapper.toEntity(dto), passwordEncoder);
        var observer = userService.saveUser(UserEntity.builder().email("observer104@example.com")
                .password(dto.getPassword()).role(dto.getRole()).build(), passwordEncoder);
        var firstDevice = deletionLogin(owner.getEmail(), dto.getPassword());
        var secondDevice = deletionLogin(owner.getEmail(), dto.getPassword());
        var otherDevice = deletionLogin(observer.getEmail(), dto.getPassword());
        var access = firstDevice.get("accessToken").asText();
        var otherAccess = otherDevice.get("accessToken").asText();
        var school = schoolService.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
        var program = programs.save(com.bowe.meetstudent.entities.Program.builder().name("Deletion program").school(school).build());
        var course = courses.save(com.bowe.meetstudent.entities.Course.builder().name("Deletion course").program(program).build());
        var targets = java.util.Map.of("school", school.getId(), "program", program.getId(), "course", course.getId());
        var removed = new java.util.HashMap<String, Integer>();
        var retained = new java.util.HashMap<String, Integer>();
        for (var target : targets.entrySet()) {
            var path = "/api/v1/" + target.getKey() + "-rates";
            var body = objectMapper.writeValueAsString(java.util.Map.of("note", 4, target.getKey() + "Id", target.getValue()));
            for (var bearer : List.of(access, otherAccess)) {
                var rate = objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.post(path)
                                .header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString());
                (bearer.equals(access) ? removed : retained).put(path, rate.get("id").asInt());
            }
        }
        // A former student can retain a wishlist after becoming an expert.
        userService.addToWishlist(owner.getId(), school.getId());
        var upload = objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/media")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4 proof".getBytes()))
                        .param("category", "DIPLOMA").header("Authorization", "Bearer " + access))
                .andExpect(MockMvcResultMatchers.status().isCreated()).andReturn().getResponse().getContentAsString());
        var actions = new java.util.HashMap<com.bowe.meetstudent.entities.enums.UserTokenPurpose, String>();
        for (var purpose : com.bowe.meetstudent.entities.enums.UserTokenPurpose.values()) {
            actions.put(purpose, actionTokens.issue(owner, purpose, java.time.Duration.ofHours(1)));
        }
        for (var body : List.of("{}", "{\"currentPassword\":\"wrong\"}")) {
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + owner.getId())
                            .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest());
            for (var device : List.of(firstDevice, secondDevice)) {
                mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + owner.getId())
                                .header("Authorization", "Bearer " + device.get("accessToken").asText()))
                        .andExpect(MockMvcResultMatchers.status().isOk());
            }
            for (var entry : removed.entrySet()) {
                mockMvc.perform(MockMvcRequestBuilders.get(entry.getKey() + "/" + entry.getValue())
                                .header("Authorization", "Bearer " + otherAccess))
                        .andExpect(MockMvcResultMatchers.status().isOk());
            }
        }
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + owner.getId())
                        .header("Authorization", "Bearer " + access).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("currentPassword", dto.getPassword()))))
                .andExpect(MockMvcResultMatchers.status().isNoContent()).andExpect(MockMvcResultMatchers.content().string(""));
        for (var action : actions.entrySet()) {
            var endpoint = switch (action.getKey()) {
                case EMAIL_VERIFICATION -> "email-verification";
                case PASSWORD_RESET -> "password-reset";
                case EMAIL_CHANGE -> "email-change";
            };
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/" + endpoint + "/confirm")
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "token", action.getValue(), "password", "replacement104", "confirmedPassword", "replacement104"))))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest())
                    .andExpect(MockMvcResultMatchers.jsonPath("$.token").value("Lien invalide ou expiré"));
        }
        for (var entry : removed.entrySet()) {
            mockMvc.perform(MockMvcRequestBuilders.get(entry.getKey() + "/" + entry.getValue())
                    .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isNotFound());
            mockMvc.perform(MockMvcRequestBuilders.get(entry.getKey() + "/" + retained.get(entry.getKey()))
                    .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isOk());
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/media/" + upload.get("id").asInt())
                .with(TestDataUtil.mockUser(999, "ROLE_ADMIN"))).andExpect(MockMvcResultMatchers.status().isNotFound());
        for (var device : List.of(firstDevice, secondDevice)) {
            mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + owner.getId())
                            .header("Authorization", "Bearer " + device.get("accessToken").asText()))
                    .andExpect(MockMvcResultMatchers.status().isUnauthorized());
            mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("refreshToken", device.get("refreshToken").asText()))))
                    .andExpect(MockMvcResultMatchers.status().isUnauthorized());
        }
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/users/id/" + observer.getId())
                .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/schools/" + school.getId())
                .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/programs/" + program.getId())
                .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isOk());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/courses/" + course.getId())
                .header("Authorization", "Bearer " + otherAccess)).andExpect(MockMvcResultMatchers.status().isOk());
    }

    private com.fasterxml.jackson.databind.JsonNode deletionLogin(String email, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("username", email, "password", password))))
                .andExpect(MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Role ensureRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleName).build()));
    }
}
