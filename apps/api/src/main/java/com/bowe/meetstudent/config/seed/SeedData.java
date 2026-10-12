package com.bowe.meetstudent.config.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Shape of {@code seed/senegal.json}. Lists may be omitted in the file (they read as empty).
 * {@code source} is documentation only: every school cites where its data comes from.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record SeedData(List<TagSeed> tags, List<AccreditationSeed> accreditations, List<SchoolSeed> schools) {

    public SeedData {
        tags = tags == null ? List.of() : tags;
        accreditations = accreditations == null ? List.of() : accreditations;
        schools = schools == null ? List.of() : schools;
    }

    public record TagSeed(String name) {}

    public record AccreditationSeed(String code, String name, String description) {}

    public record SchoolSeed(String name, String code, String city, String location, String country,
                             List<String> tags, String source, List<ProgramSeed> programs) {
        public SchoolSeed {
            tags = tags == null ? List.of() : tags;
            programs = programs == null ? List.of() : programs;
        }
    }

    public record ProgramSeed(String name, Integer duration, List<CourseSeed> courses,
                              List<AccreditationLinkSeed> accreditations) {
        public ProgramSeed {
            courses = courses == null ? List.of() : courses;
            accreditations = accreditations == null ? List.of() : accreditations;
        }
    }

    public record CourseSeed(String name) {}

    public record AccreditationLinkSeed(String code, Integer starts, Integer ends) {}
}
