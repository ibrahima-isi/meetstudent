package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SiteSettingsControllerIntegrationTests {

    private static final String URL = "/api/v1/site-settings";

    @Autowired
    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.ResultActions patchAsAdmin(String json) throws Exception {
        return mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(json)
                .with(TestDataUtil.mockUser("ROLE_ADMIN")));
    }

    @Test
    void anonymousVisitorReadsTheDefaultsBeforeAnyEdit() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"))
                .andExpect(jsonPath("$.aboutFr").value(not(blankOrNullString())))
                .andExpect(jsonPath("$.aboutEn").value(not(blankOrNullString())));
    }

    @Test
    void adminChangesTheContactEmailAndEveryoneSeesIt() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"hello@example.sn\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("hello@example.sn"));

        mockMvc.perform(get(URL)).andExpect(jsonPath("$.contactEmail").value("hello@example.sn"));
    }

    @Test
    void changingOneLanguageLeavesTheOtherAndTheEmailAlone() throws Exception {
        patchAsAdmin("{\"aboutEn\":\"English only.\"}").andExpect(status().isOk());
        patchAsAdmin("{\"aboutFr\":\"Premier paragraphe.\\n\\nSecond paragraphe.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutFr").value("Premier paragraphe.\n\nSecond paragraphe."))
                .andExpect(jsonPath("$.aboutEn").value("English only."))
                .andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"));
    }

    @Test
    void anonymousAndNonAdminCannotWrite() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"x@example.sn\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content("{\"contactEmail\":\"x@example.sn\"}")
                        .with(TestDataUtil.mockUser("ROLE_STUDENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(URL)).andExpect(jsonPath("$.contactEmail").value("contact@meetstudent.com"));
    }

    @Test
    void rejectsAnInvalidEmailABlankTextAndAnOverlongText() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.contactEmail").exists());
        patchAsAdmin("{\"contactEmail\":\"   \"}").andExpect(status().isBadRequest());
        patchAsAdmin("{\"aboutFr\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.aboutFr").exists());
        patchAsAdmin("{\"aboutEn\":\"" + "a".repeat(5001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.aboutEn").exists());
        patchAsAdmin("{\"aboutEn\":\"" + "a".repeat(5000) + "\"}").andExpect(status().isOk());
    }

    @Test
    void storesTextAsGivenWithoutTheSurroundingBlanks() throws Exception {
        patchAsAdmin("{\"contactEmail\":\"  Hello@Example.sn \",\"aboutFr\":\"  <b>Bonjour</b>  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("Hello@Example.sn"))
                .andExpect(jsonPath("$.aboutFr").value("<b>Bonjour</b>"));
    }
}
