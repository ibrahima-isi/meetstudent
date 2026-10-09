package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The note is a 1..5 star rating; out-of-range or missing notes must be rejected with 400. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateValidationIntegrationTests {

    @Autowired private MockMvc mockMvc;

    private void assertRejected(String url, String idField, String note, RequestPostProcessor user, String expectedMessage) throws Exception {
        String body = "{" + (note == null ? "" : "\"note\":" + note + ",") + "\"" + idField + "\":1}";
        mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body).with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.note").value(expectedMessage));
    }

    @Test
    void schoolRateAboveFiveIsRejected() throws Exception {
        assertRejected("/api/v1/school-rates", "schoolId", "9", TestDataUtil.mockUser("ROLE_STUDENT"), "Note cannot exceed 5");
    }

    @Test
    void schoolRateBelowOneIsRejected() throws Exception {
        assertRejected("/api/v1/school-rates", "schoolId", "0", TestDataUtil.mockUser("ROLE_STUDENT"), "Note must be at least 1");
    }

    @Test
    void schoolRateWithoutNoteIsRejected() throws Exception {
        assertRejected("/api/v1/school-rates", "schoolId", null, TestDataUtil.mockUser("ROLE_STUDENT"), "Note is required");
    }

    @Test
    void programRateAboveFiveIsRejected() throws Exception {
        assertRejected("/api/v1/program-rates", "programId", "9", TestDataUtil.mockUser("ROLE_EXPERT"), "Note cannot exceed 5");
    }

    @Test
    void courseRateAboveFiveIsRejected() throws Exception {
        assertRejected("/api/v1/course-rates", "courseId", "9", TestDataUtil.mockUser("ROLE_EXPERT"), "Note cannot exceed 5");
    }
}
