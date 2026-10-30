package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.support.ResetStateExtension;
import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.TagDTO;
import com.bowe.meetstudent.services.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import java.util.UUID;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ExtendWith(ResetStateExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TagControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagService tagService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testThatCreateTagReturnsHttpStatus201() throws Exception {
        TagDTO tagDTO = TagDTO.builder().name("ENGINEERING").build();
        String json = objectMapper.writeValueAsString(tagDTO);

        mockMvc.perform(
                MockMvcRequestBuilders.post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isCreated()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("ENGINEERING")
        );
    }

    @Test
    void duplicateTagReturnsConflictWithoutLeakingDatabaseDetails() throws Exception {
        tagService.findOrCreate("ENGINEERING");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ENGINEERING\"}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
    }

    @Test
    void tagWithoutNameReturnsFieldError() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").isNotEmpty());
    }

    @Test
    void deletingATagUsedBySchoolReturnsConflictAndKeepsBoth() throws Exception {
        var tag = tagService.findOrCreate("USED_TAG");
        String schoolCode = UUID.randomUUID().toString().substring(0, 5);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tagged school\",\"code\":\"" + schoolCode
                                + "\",\"tags\":[{\"id\":" + tag.getId() + ",\"name\":\"USED_TAG\"}]}")
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/tags/" + tag.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tags/" + tag.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN")))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testThatGetAllTagsReturnsList() throws Exception {
        tagService.findOrCreate("PUBLIC");
        tagService.findOrCreate("PRIVATE");

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/tags")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.length()").value(2)
        );
    }

    @Test
    void testThatGetTagByIdReturnsHttpStatus200() throws Exception {
        com.bowe.meetstudent.entities.Tag tag = tagService.findOrCreate("GRANDE_ECOLE");

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/tags/" + tag.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isOk()
        ).andExpect(
                MockMvcResultMatchers.jsonPath("$.name").value("GRANDE_ECOLE")
        );
    }

    @Test
    void testThatDeleteTagReturnsHttpStatus204() throws Exception {
        com.bowe.meetstudent.entities.Tag tag = tagService.findOrCreate("TEMPORARY");

        mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/tags/" + tag.getId())
                        .with(TestDataUtil.mockUser("ROLE_ADMIN"))
        ).andExpect(
                MockMvcResultMatchers.status().isNoContent()
        );

        mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v1/tags/" + tag.getId())
                        .with(TestDataUtil.mockUser("ROLE_STUDENT"))
        ).andExpect(
                MockMvcResultMatchers.status().isNotFound()
        );
    }
}
