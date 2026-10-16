package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Duplicate / in-use writes answer 409 and a few client errors answer 4xx instead of a generic 500.
 * Data is created through the API itself with unique values, so the tests share one cached context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConflictAndClientErrorStatusIntegrationTests {

    private static final String ADMIN = "ROLE_ADMIN";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Five characters, unique enough for a handful of rows per run. */
    private static String code() {
        return unique().substring(0, 5);
    }

    private ResultActions send(MockHttpServletRequestBuilder req, String role, String json) throws Exception {
        if (json != null) req.contentType(MediaType.APPLICATION_JSON).content(json);
        if (role != null) req.with(TestDataUtil.mockUser(1, role));
        return mockMvc.perform(req);
    }

    private int idOf(ResultActions result) throws Exception {
        JsonNode node = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return node.get("id").asInt();
    }

    private int create(String path, String name, String code) throws Exception {
        return idOf(send(post(path), ADMIN, "{\"name\":\"" + name + "\",\"code\":\"" + code + "\"}")
                .andExpect(status().isCreated()));
    }

    private int createTag(String name) throws Exception {
        return idOf(send(post("/api/v1/tags"), ADMIN, "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated()));
    }

    private void expectConflict(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message", not(containsString("constraint"))))
                .andExpect(jsonPath("$.message", not(containsString("SQL"))))
                .andExpect(jsonPath("$.message", not(containsString("INSERT"))));
    }

    @Test
    void creatingADuplicateTagAnswers409() throws Exception {
        String name = "TAG" + unique();
        createTag(name);

        expectConflict(send(post("/api/v1/tags"), ADMIN, "{\"name\":\"" + name + "\"}"));
    }

    @Test
    void deletingATagStillUsedBySchoolAnswers409AndKeepsIt() throws Exception {
        String name = "USED" + unique();
        int tagId = createTag(name);
        send(post("/api/v1/schools"), ADMIN,
                "{\"name\":\"Tagged\",\"code\":\"" + code() + "\",\"tags\":[{\"id\":" + tagId + ",\"name\":\"" + name + "\"}]}")
                .andExpect(status().isCreated());

        expectConflict(send(delete("/api/v1/tags/" + tagId), ADMIN, null));

        send(get("/api/v1/tags/" + tagId), ADMIN, null).andExpect(status().isOk());
    }

    @Test
    void duplicateSchoolCodeAnswers409OnCreateAndUpdate() throws Exception {
        String taken = code();
        create("/api/v1/schools", "School " + taken, taken);

        expectConflict(send(post("/api/v1/schools"), ADMIN, "{\"name\":\"Twin\",\"code\":\"" + taken + "\"}"));

        String otherCode = code();
        int other = create("/api/v1/schools", "School " + otherCode, otherCode);
        expectConflict(send(put("/api/v1/schools/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
        expectConflict(send(patch("/api/v1/schools/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
    }

    @Test
    void duplicateProgramCodeAnswers409OnCreateAndUpdate() throws Exception {
        String taken = code();
        create("/api/v1/programs", "Program " + taken, taken);

        expectConflict(send(post("/api/v1/programs"), ADMIN, "{\"name\":\"Twin\",\"code\":\"" + taken + "\"}"));

        String otherCode = code();
        int other = create("/api/v1/programs", "Program " + otherCode, otherCode);
        expectConflict(send(put("/api/v1/programs/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
        expectConflict(send(patch("/api/v1/programs/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
    }

    @Test
    void duplicateCourseCodeAnswers409OnCreateAndUpdate() throws Exception {
        String taken = code();
        create("/api/v1/courses", "Course " + taken, taken);

        expectConflict(send(post("/api/v1/courses"), ADMIN, "{\"name\":\"Twin\",\"code\":\"" + taken + "\"}"));

        String otherCode = code();
        int other = create("/api/v1/courses", "Course " + otherCode, otherCode);
        expectConflict(send(put("/api/v1/courses/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
        expectConflict(send(patch("/api/v1/courses/" + other), ADMIN, "{\"code\":\"" + taken + "\"}"));
    }

    @Test
    void sortingByAnUnknownPropertyAnswers400() throws Exception {
        send(get("/api/v1/schools?sort=doesNotExist,asc"), "ROLE_STUDENT", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void anUnacceptableAcceptHeaderAnswers406WithTheJsonErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/tags").accept(MediaType.APPLICATION_XML)
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("Not Acceptable"));
    }

    @Test
    void aSchoolReferencingAnUnknownTagAnswers400OnCreateAndUpdate() throws Exception {
        String body = "{\"name\":\"Ghost tag\",\"code\":\"" + code() + "\",\"tags\":[{\"id\":987654}]}";
        send(post("/api/v1/schools"), ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tags").isNotEmpty());

        String c = code();
        int id = create("/api/v1/schools", "School " + c, c);
        String update = "{\"tags\":[{\"id\":987654}]}";
        send(put("/api/v1/schools/" + id), ADMIN, update)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tags").isNotEmpty());
        send(patch("/api/v1/schools/" + id), ADMIN, update)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tags").isNotEmpty());
    }

    @Test
    void anUnknownSchoolWithAnUnknownTagAnswers404() throws Exception {
        send(put("/api/v1/schools/987654"), ADMIN, "{\"tags\":[{\"id\":987655}]}")
                .andExpect(status().isNotFound());
    }

    @Test
    void putWithAValidTagKeepsItAndAnEmptyListClearsTags() throws Exception {
        String name = "VALID" + unique();
        int tagId = createTag(name);
        String c = code();
        int id = create("/api/v1/schools", "School " + c, c);

        send(put("/api/v1/schools/" + id), ADMIN, "{\"tags\":[{\"id\":" + tagId + "},{\"id\":" + tagId + "}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2));
        send(put("/api/v1/schools/" + id), ADMIN, "{\"tags\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void theUnknownSortMessageNamesAndTruncatesTheProperty() throws Exception {
        send(get("/api/v1/schools?sort=doesNotExist,asc"), "ROLE_STUDENT", null)
                .andExpect(jsonPath("$.message").value("Unknown sort property 'doesNotExist'"));
        send(get("/api/v1/schools?sort=" + "x".repeat(200)), "ROLE_STUDENT", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sort property '" + "x".repeat(64) + "'"));
    }
}
