package com.bowe.meetstudent.unit.config;

import com.bowe.meetstudent.config.seed.DevSeedRunner;
import com.bowe.meetstudent.config.seed.SeedData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the shipped seed file against what the schema (and honesty about sources) requires. */
class SeedDataFileTest {

    private final SeedData data = read();

    private static SeedData read() {
        try (var in = new ClassPathResource("seed/senegal.json").getInputStream()) {
            return new ObjectMapper().readValue(in, SeedData.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void seedsOnlyOnDevAndDocker() {
        var profile = DevSeedRunner.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"dev", "docker"}, profile.value());
    }

    @Test
    void schoolsFitTheSchemaAndAreUnique() {
        assertFalse(data.schools().isEmpty());
        var names = new HashSet<String>();
        var codes = new HashSet<String>();
        for (var s : data.schools()) {
            assertTrue(s.name() != null && !s.name().isBlank() && s.name().length() <= 50, "name: " + s.name());
            assertTrue(names.add(s.name().toLowerCase()), "duplicate school: " + s.name());
            if (s.code() != null) {
                assertTrue(s.code().length() <= 5, "code too long: " + s.code());
                assertTrue(codes.add(s.code().toUpperCase()), "duplicate code: " + s.code());
            }
            assertTrue(s.city() != null && !s.city().isBlank(), "city missing: " + s.name());
            assertTrue(s.city().length() <= 255 && (s.location() == null || s.location().length() <= 255));
        }
    }

    @Test
    void everySchoolCitesItsSource() {
        for (var s : data.schools()) {
            assertTrue(s.source() != null && s.source().startsWith("http"), "no source for " + s.name());
        }
    }

    @Test
    void programsFitTheSchemaAndAreUniquePerSchool() {
        for (var s : data.schools()) {
            var seen = new HashSet<String>();
            for (var p : s.programs()) {
                assertTrue(p.name() != null && !p.name().isBlank() && p.name().length() <= 50,
                        "program name too long/blank in " + s.name() + ": " + p.name());
                assertTrue(seen.add(p.name().toLowerCase()), "duplicate program in " + s.name() + ": " + p.name());
                assertTrue(p.duration() == null || (p.duration() >= 1 && p.duration() <= 10));
                for (var c : p.courses()) {
                    assertTrue(c.name() != null && c.name().length() <= 50, "course name: " + c.name());
                }
            }
        }
    }

    @Test
    void everyReferencedTagAndAccreditationIsDeclared() {
        var tags = data.tags().stream().map(SeedData.TagSeed::name).collect(Collectors.toSet());
        var codes = data.accreditations().stream().map(SeedData.AccreditationSeed::code).collect(Collectors.toSet());
        for (var s : data.schools()) {
            for (var t : s.tags()) assertTrue(tags.contains(t), "undeclared tag " + t + " on " + s.name());
            for (var p : s.programs()) {
                for (var a : p.accreditations()) assertTrue(codes.contains(a.code()), "undeclared accreditation " + a.code());
            }
        }
        for (var a : data.accreditations()) {
            assertTrue(a.code().length() <= 5 && a.name().length() <= 50, a.code());
            assertTrue(a.description() == null || a.description().length() <= 255, a.code());
        }
    }
}
