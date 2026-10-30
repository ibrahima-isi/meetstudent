package com.bowe.meetstudent.integration.controllers;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.mappers.implementations.CourseMapper;
import com.bowe.meetstudent.services.CourseService;
import com.bowe.meetstudent.support.ResetStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public catalogue lists accept only the {@code ?sort=} properties they declare. A free sort lets anyone order a
 * list by a column reached through an association, such as the e-mail of the users who rated a school: the response
 * never shows that column, but the order of the rows does, one comparison at a time.
 */
@SpringBootTest
@ExtendWith(ResetStateExtension.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogueSortAllowlistIntegrationTests {

    private static final String SCHOOL_RATER = "schoolRates.userEntity.email";
    private static final String COURSE_ACCREDITATIONS = "/api/v1/courses/{courseId}/accreditations";

    private static final List<String> SCHOOL_LISTS = List.of("/api/v1/schools", "/api/v1/schools?sortRate=most",
            "/api/v1/schools/name/a", "/api/v1/schools/address/Dakar", "/api/v1/schools/address/country/Senegal",
            "/api/v1/schools/search?city=Dakar");
    private static final List<String> PROGRAM_LISTS =
            List.of("/api/v1/programs", "/api/v1/programs?sortRate=most", "/api/v1/programs/name/a");
    private static final List<String> COURSE_LISTS =
            List.of("/api/v1/courses", "/api/v1/courses?sortRate=most", "/api/v1/courses/name/a");
    private static final List<String> ACCREDITATION_LISTS =
            List.of("/api/v1/accreditations", "/api/v1/accreditations/name/a");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseMapper courseMapper;

    // The course accreditations list answers 404 for an unknown course, so it needs a real one.
    private int courseId;

    @BeforeEach
    void saveACourse() {
        courseId = courseService.save(courseMapper.toEntity(TestDataUtil.createCourseDto())).getId();
    }

    // Spring Data also reads camel case and underscores as path separators: refusing dots alone would not be enough.
    static Stream<Arguments> refusedSorts() {
        return Stream.of(
                each(SCHOOL_LISTS, SCHOOL_RATER),
                // Existing columns the response never shows are not listed either.
                each(List.of("/api/v1/schools"), "schoolRatesUserEntityEmail", "schoolRates_userEntity_email", "createdBy",
                        "creation"),
                each(PROGRAM_LISTS, "school." + SCHOOL_RATER, "programRates.userEntity.email"),
                each(COURSE_LISTS, "program.school." + SCHOOL_RATER, "courseRates.userEntity.email"),
                each(ACCREDITATION_LISTS, "programAccreditations.program.school." + SCHOOL_RATER),
                each(List.of(COURSE_ACCREDITATIONS), "course.program.school." + SCHOOL_RATER, "accreditation.name")
        ).flatMap(cases -> cases);
    }

    static Stream<Arguments> allowedSorts() {
        return Stream.of(
                each(SCHOOL_LISTS, "id", "name", "code", "address.city", "address.country"),
                each(PROGRAM_LISTS, "id", "name", "code", "duration", "level", "annualFee", "availableSeats"),
                each(COURSE_LISTS, "id", "name", "code"),
                each(ACCREDITATION_LISTS, "id", "name", "code"),
                each(List.of(COURSE_ACCREDITATIONS), "startsAt", "endsAt")
        ).flatMap(cases -> cases);
    }

    private static Stream<Arguments> each(List<String> urls, String... sorts) {
        return urls.stream().flatMap(url -> Stream.of(sorts).map(sort -> Arguments.of(url, sort)));
    }

    @ParameterizedTest(name = "{0} ?sort={1}")
    @MethodSource("refusedSorts")
    void aSortOffTheListAnswers400LikeAnUnknownProperty(String url, String sort) throws Exception {
        mockMvc.perform(get(url, courseId).param("sort", sort + ",asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown sort property '" + truncated(sort) + "'"));
    }

    @ParameterizedTest(name = "{0} ?sort={1}")
    @MethodSource("allowedSorts")
    void aListedSortIsStillAccepted(String url, String sort) throws Exception {
        mockMvc.perform(get(url, courseId).param("sort", sort + ",desc"))
                .andExpect(status().isOk());
    }

    @Test
    void everyRequestedSortPropertyIsChecked() throws Exception {
        mockMvc.perform(get("/api/v1/schools").param("sort", "name,asc").param("sort", SCHOOL_RATER + ",asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/schools").param("sort", "name," + SCHOOL_RATER + ",asc"))
                .andExpect(status().isBadRequest());
        // What the web sends for its city order.
        mockMvc.perform(get("/api/v1/schools").param("sort", "address.city,asc").param("sort", "id,asc"))
                .andExpect(status().isOk());
    }

    // The error message keeps the first 64 characters of the property.
    private static String truncated(String sort) {
        return sort.length() <= 64 ? sort : sort.substring(0, 64);
    }
}
