package com.bowe.meetstudent.integration.controllers;

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
