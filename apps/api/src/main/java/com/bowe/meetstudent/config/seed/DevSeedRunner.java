package com.bowe.meetstudent.config.seed;

import com.bowe.meetstudent.entities.Accreditation;
import com.bowe.meetstudent.entities.Course;
import com.bowe.meetstudent.entities.Program;
import com.bowe.meetstudent.entities.ProgramAccreditation;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.Tag;
import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.entities.embedded.ProgramAccreditationId;
import com.bowe.meetstudent.repositories.AccreditationRepository;
import com.bowe.meetstudent.repositories.ProgramAccreditationRepository;
import com.bowe.meetstudent.repositories.SchoolRepository;
import com.bowe.meetstudent.repositories.TagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the reference data (tags, accreditations, schools, programs, courses and the
 * program/accreditation links) from {@code seed/senegal.json} so a local stack has something to
 * browse. Never runs under {@code prod} or {@code test}; never creates users or ratings.
 *
 * <p>Idempotent and additive: every record is looked up by its natural key (tag name,
 * accreditation code, school name, program name within its school, course name within its program,
 * program + accreditation) and only created when missing. Existing rows, including ones edited in
 * the back office, are never modified.
 */
@Slf4j
@Component
@Profile({"dev", "docker"})
public class DevSeedRunner implements ApplicationRunner {

    private final SchoolRepository schoolRepository;
    private final TagRepository tagRepository;
    private final AccreditationRepository accreditationRepository;
    private final ProgramAccreditationRepository programAccreditationRepository;
    private final ObjectMapper objectMapper;
    private final Resource seedFile;

    @Autowired
    public DevSeedRunner(SchoolRepository schoolRepository,
                         TagRepository tagRepository,
                         AccreditationRepository accreditationRepository,
                         ProgramAccreditationRepository programAccreditationRepository,
                         ObjectMapper objectMapper,
                         @Value("${app.seed.location:classpath:seed/senegal.json}") Resource seedFile) {
        this.schoolRepository = schoolRepository;
        this.tagRepository = tagRepository;
        this.accreditationRepository = accreditationRepository;
        this.programAccreditationRepository = programAccreditationRepository;
        this.objectMapper = objectMapper;
        this.seedFile = seedFile;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        SeedData data;
        try (var in = seedFile.getInputStream()) {
            data = objectMapper.readValue(in, SeedData.class);
        }

        Map<String, Tag> tags = new HashMap<>();
        for (var seed : data.tags()) {
            tags.put(seed.name().toLowerCase(), tagRepository.findByNameIgnoreCase(seed.name())
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(seed.name()).build())));
        }

        Map<String, Accreditation> accreditations = new HashMap<>();
        for (var seed : data.accreditations()) {
            accreditations.put(seed.code().toLowerCase(), accreditationRepository.findByCodeIgnoreCase(seed.code())
                    .orElseGet(() -> accreditationRepository.save(Accreditation.builder()
                            .code(seed.code()).name(seed.name()).description(seed.description()).build())));
        }

        int created = 0;
        for (var seed : data.schools()) {
            var school = schoolRepository.findByNameIgnoreCase(seed.name()).orElse(null);
            if (school == null) {
                school = School.builder()
                        .name(seed.name())
                        .code(seed.code())
                        .address(Address.builder().city(seed.city()).location(seed.location()).country(seed.country()).build())
                        .tags(new ArrayList<>(seed.tags().stream().map(t -> tags.get(t.toLowerCase())).toList()))
                        .programs(new ArrayList<>())
                        .build();
                created++;
            }
            for (var programSeed : seed.programs()) {
                addProgramIfMissing(school, programSeed);
            }
            school = schoolRepository.save(school);
            linkAccreditations(school, seed, accreditations);
        }
        log.info("Seed {} loaded: {} new school(s), {} school(s) in the file.", seedFile.getFilename(), created, data.schools().size());
    }

    private void addProgramIfMissing(School school, SeedData.ProgramSeed seed) {
        if (school.getPrograms() == null) {
            school.setPrograms(new ArrayList<>());
        }
        var program = school.getPrograms().stream()
                .filter(p -> seed.name().equalsIgnoreCase(p.getName())).findFirst().orElse(null);
        if (program == null) {
            program = Program.builder().name(seed.name()).duration(seed.duration()).courses(new ArrayList<>()).build();
            school.addProgram(program);
        }
        if (program.getCourses() == null) {
            program.setCourses(new ArrayList<>());
        }
        for (var courseSeed : seed.courses()) {
            var existing = program.getCourses();
            if (existing.stream().noneMatch(c -> courseSeed.name().equalsIgnoreCase(c.getName()))) {
                program.addCourse(Course.builder().name(courseSeed.name()).build());
            }
        }
    }

    /** Links need the program and accreditation ids, so they are written once the school is saved. */
    private void linkAccreditations(School school, SeedData.SchoolSeed seed, Map<String, Accreditation> accreditations) {
        for (var programSeed : seed.programs()) {
            if (programSeed.accreditations().isEmpty()) {
                continue;
            }
            var program = school.getPrograms().stream()
                    .filter(p -> programSeed.name().equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            for (var link : programSeed.accreditations()) {
                var accreditation = accreditations.get(link.code().toLowerCase());
                var id = new ProgramAccreditationId(program.getId(), accreditation.getId());
                if (programAccreditationRepository.existsById(id)) {
                    continue;
                }
                var programAccreditation = new ProgramAccreditation();
                programAccreditation.setId(id);
                programAccreditation.setProgram(program);
                programAccreditation.setAccreditation(accreditation);
                programAccreditation.setStartsAt(link.starts());
                programAccreditation.setEndsAt(link.ends());
                programAccreditationRepository.save(programAccreditation);
            }
        }
    }
}
