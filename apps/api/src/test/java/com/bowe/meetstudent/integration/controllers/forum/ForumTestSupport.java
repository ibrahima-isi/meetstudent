package com.bowe.meetstudent.integration.controllers.forum;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.entities.ForumReply;
import com.bowe.meetstudent.entities.ForumThread;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.School;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.mappers.implementations.SchoolMapper;
import com.bowe.meetstudent.repositories.ForumReplyRepository;
import com.bowe.meetstudent.repositories.ForumThreadRepository;
import com.bowe.meetstudent.repositories.RoleRepository;
import com.bowe.meetstudent.services.SchoolService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared fixtures of the forum integration tests: users by role, schools, and direct seeding of messages. */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ForumTestSupport {

    static final String PASSWORD = "password";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleRepository roles;
    @Autowired UserService userService;
    @Autowired com.bowe.meetstudent.repositories.UserRepository userRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired SchoolService schools;
    @Autowired SchoolMapper schoolMapper;
    @Autowired ForumThreadRepository threads;
    @Autowired ForumReplyRepository replies;

    private Role role(String name) {
        return roles.findByName(name).orElseGet(() -> roles.save(Role.builder().name(name).build()));
    }

    private UserEntity user(String roleName, ExpertStatus status, String firstname) {
        var n = SEQUENCE.incrementAndGet();
        return userService.saveUser(UserEntity.builder().firstname(firstname).lastname("Secret" + n)
                .email("forum" + n + "@example.com").password(PASSWORD).role(role(roleName)).expertStatus(status).build(), encoder);
    }

    UserEntity student(String firstname) { return user("ROLE_STUDENT", null, firstname); }
    UserEntity student() { return student("Awa"); }
    UserEntity approvedExpert() { return user("ROLE_EXPERT", ExpertStatus.APPROVED, "Moussa"); }
    UserEntity pendingExpert() { return user("ROLE_EXPERT", ExpertStatus.PENDING, "Pending"); }
    UserEntity rejectedExpert() { return user("ROLE_EXPERT", ExpertStatus.REJECTED, "Rejected"); }
    UserEntity admin() { return user("ROLE_ADMIN", null, "Boss"); }

    School school() {
        return schools.save(schoolMapper.toEntity(TestDataUtil.createSchoolDto()));
    }

    static RequestPostProcessor as(UserEntity u) {
        return TestDataUtil.mockUser(u.getId(), u.getRole().getName());
    }

    ForumThread thread(UserEntity author, School school, String title, Instant createdAt) {
        return threads.save(ForumThread.builder().title(title).body("Body of " + title).author(author).school(school)
                .createdAt(createdAt).build());
    }

    ForumThread thread(UserEntity author, String title) {
        return thread(author, null, title, Instant.now());
    }

    ForumReply reply(ForumThread thread, UserEntity author, String body, Instant createdAt) {
        return replies.save(ForumReply.builder().thread(thread).author(author).body(body).createdAt(createdAt).build());
    }

    ForumReply reply(ForumThread thread, UserEntity author, String body) {
        return reply(thread, author, body, Instant.now());
    }

    String threadBody(String title, String body, Integer schoolId) throws Exception {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("title", title);
        map.put("body", body);
        if (schoolId != null) map.put("schoolId", schoolId);
        return json.writeValueAsString(map);
    }

    String replyBody(String body) throws Exception {
        return json.writeValueAsString(Map.of("body", body));
    }
}
