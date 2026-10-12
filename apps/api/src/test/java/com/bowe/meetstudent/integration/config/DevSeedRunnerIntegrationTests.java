package com.bowe.meetstudent.integration.config;

import com.bowe.meetstudent.config.seed.DevSeedRunner;
import com.bowe.meetstudent.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** The seeder against a real JPA stack (H2); the bean itself is dev/docker-only. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevSeedRunnerIntegrationTests {

    @Autowired private SchoolRepository schools;
    @Autowired private ProgramRepository programs;
    @Autowired private CourseRepository courses;
    @Autowired private TagRepository tags;
    @Autowired private AccreditationRepository accreditations;
    @Autowired private ProgramAccreditationRepository links;
    @Autowired private UserRepository users;
    @Autowired private ObjectMapper objectMapper;

    private DevSeedRunner runner() {
        return new DevSeedRunner(schools, tags, accreditations, links, objectMapper,
                new ClassPathResource("seed/test-seed.json"));
    }

    @Test
    void createsEveryEntityFromTheSeedFile() throws Exception {
        long usersBefore = users.count();
        runner().run(null);

        var school = schools.findByNameIgnoreCase("École Test de Dakar").orElseThrow();
        assertEquals("ETD", school.getCode());
        assertEquals("Dakar", school.getAddress().getCity());
        assertEquals("Avenue Test", school.getAddress().getLocation());
        assertEquals(2, school.getTags().size());
        assertEquals(2, school.getPrograms().size());

        var licence = school.getPrograms().stream()
                .filter(p -> p.getName().equals("Licence en Informatique")).findFirst().orElseThrow();
        assertEquals(3, licence.getDuration());
        assertEquals(2, licence.getCourses().size());
        var link = links.findByProgramId(licence.getId()).getFirst();
        assertEquals("CAMES", link.getAccreditation().getCode());
        assertEquals(2020, link.getStartsAt());
        assertEquals(2025, link.getEndsAt());

        assertTrue(schools.findByNameIgnoreCase("Lycée Test de Thiès").orElseThrow().getPrograms().isEmpty());
        assertEquals(usersBefore, users.count(), "the seed never creates or touches users");
    }

    @Test
    void runningTwiceChangesNothing() throws Exception {
        runner().run(null);
        long schoolCount = schools.count(), programCount = programs.count(), courseCount = courses.count(),
                tagCount = tags.count(), accreditationCount = accreditations.count(), linkCount = links.count();

        runner().run(null);

        assertEquals(schoolCount, schools.count());
        assertEquals(programCount, programs.count());
        assertEquals(courseCount, courses.count());
        assertEquals(tagCount, tags.count());
        assertEquals(accreditationCount, accreditations.count());
        assertEquals(linkCount, links.count());
    }

    @Test
    void addsMissingProgramsToAnExistingSchool() throws Exception {
        runner().run(null);
        var school = schools.findByNameIgnoreCase("École Test de Dakar").orElseThrow();
        school.getPrograms().removeIf(p -> p.getName().equals("Master en Réseaux"));
        schools.saveAndFlush(school);

        runner().run(null);

        assertEquals(2, schools.findByNameIgnoreCase("École Test de Dakar").orElseThrow().getPrograms().size());
    }
}
