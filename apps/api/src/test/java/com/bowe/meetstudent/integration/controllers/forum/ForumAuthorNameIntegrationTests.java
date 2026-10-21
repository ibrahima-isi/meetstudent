package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.entities.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The first name is shown on the forum, so registration and profile updates refuse hidden characters. */
class ForumAuthorNameIntegrationTests extends ForumTestSupport {

    private String register(String firstname, String email) throws Exception {
        return json.writeValueAsString(Map.of("firstname", firstname, "lastname", "Diop", "email", email,
                "password", "password109", "confirmedPassword", "password109"));
    }

    private void studentRole() {
        roles.findByName("ROLE_STUDENT").orElseGet(() -> roles.save(Role.builder().name("ROLE_STUDENT").build()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u202E", "\n", "\u200B", "\t", "\u3164", "\u0000"})
    void registrationRefusesHiddenCharactersInTheFirstName(String bad) throws Exception {
        studentRole();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(register("Aw" + bad + "a", "bad" + Math.abs(bad.hashCode()) + "@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.firstname").exists());
    }

    @Test
    void registrationRefusesAFirstNameOver100Characters() throws Exception {
        studentRole();
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content(register("a".repeat(101), "long@example.com")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.firstname").exists());
    }

    @Test
    void legitimateFirstNamesStillPass() throws Exception {
        studentRole();
        var n = 0;
        for (var name : new String[] {"Jean-François", "Ève Marie", "O'Neil", "Ibrahima", "Zoë", "Mamadou Lamine", "Aïssatou", "李雷", "a".repeat(100)}) {
            mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                            .content(register(name, "ok" + (n++) + "@example.com")))
                    .andExpect(status().isCreated());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u202E", "\n", "\u3164"})
    void profileUpdatesRefuseHiddenCharactersInTheFirstName(String bad) throws Exception {
        var user = student();
        var body = json.writeValueAsString(Map.of("firstname", "Aw" + bad + "a", "lastname", "Diop"));
        mvc.perform(put("/api/v1/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON).content(body).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.firstname").exists());
        mvc.perform(patch("/api/v1/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON).content(body).with(as(user)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.firstname").exists());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFirstname()).isEqualTo("Awa");
    }

    @Test
    void profileUpdatesKeepAcceptingALegitimateFirstName() throws Exception {
        var user = student();
        mvc.perform(patch("/api/v1/users/" + user.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstname\":\"Jean-François d'Ève\"}").with(as(user)))
                .andExpect(status().isOk());
    }
}
