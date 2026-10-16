package com.bowe.meetstudent.integration.controllers.rates;

import com.bowe.meetstudent.TestDataUtil;
import com.bowe.meetstudent.dto.ProgramRateDTO;
import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.ProgramMapper;
import com.bowe.meetstudent.services.RoleService;
import com.bowe.meetstudent.services.ProgramService;
import com.bowe.meetstudent.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs on H2 in the full suite and on migrated PostgreSQL with datasource overrides. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProgramRatePostgresConcurrencyIntegrationTests.DatabaseBarrier.class)
class ProgramRatePostgresConcurrencyIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RoleService roles;
    @Autowired UserService users;
    @Autowired PasswordEncoder encoder;
    @Autowired ProgramService programs;
    @Autowired ProgramMapper programMapper;

    @Test
    void concurrentFirstRatingsReturnOneCreationAndOneConflictThenRetryUpdatesTheWinner() throws Exception {
        var role = roles.findRoleByName("ROLE_EXPERT").orElseGet(() -> roles.createRole(Role.builder().name("ROLE_EXPERT").build()));
        var user = users.saveUser(UserEntity.builder().email(UUID.randomUUID() + "@example.com")
                .password("password").role(role).build(), encoder);
        var program = programs.save(programMapper.toEntity(TestDataUtil.createProgramDto()));
        var barrier = new InsertBarrier();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> racingRating(user.getId(), program.getId(), 2.0, barrier));
            var second = pool.submit(() -> racingRating(user.getId(), program.getId(), 5.0, barrier));
            try {
                assertThat(barrier.ready.await(15, TimeUnit.SECONDS))
                        .as("both requests reached a real INSERT after looking up the absent rating").isTrue();
            } finally { barrier.resume.countDown(); }
            var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 409);
            var created = outcomes.stream().filter(r -> r.getStatus() == 201).findFirst().orElseThrow();
            var conflict = outcomes.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            var winner = json.readValue(created.getContentAsString(), ProgramRateDTO.class);
            var error = json.readTree(conflict.getContentAsString());
            assertThat(error.get("status").asInt()).isEqualTo(409);
            assertThat(error.get("path").asText()).isEqualTo("/api/v1/program-rates");
            mvc.perform(get("/api/v1/program-rates/program/" + program.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(winner.getId()))
                    .andExpect(jsonPath("$[0].note").value(winner.getNote()));
            mvc.perform(get("/api/v1/programs/" + program.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageRate").value(winner.getNote()));
            var retry = ProgramRateDTO.builder().userId(user.getId()).programId(program.getId()).note(4.0).build();
            mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(retry)).with(TestDataUtil.mockUser(user.getId(), "ROLE_EXPERT")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(winner.getId()))
                    .andExpect(jsonPath("$.note").value(4.0));
            mvc.perform(get("/api/v1/program-rates/program/" + program.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].note").value(4.0));
            mvc.perform(get("/api/v1/programs/" + program.getId())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageRate").value(4.0));
        }
    }

    private MockHttpServletResponse racingRating(Integer userId, Integer programId, double note, InsertBarrier barrier) throws Exception {
        DatabaseBarrier.active.set(barrier);
        try {
            var body = ProgramRateDTO.builder().userId(userId).programId(programId).note(note).build();
            return mvc.perform(post("/api/v1/program-rates").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)).with(TestDataUtil.mockUser(userId, "ROLE_EXPERT")))
                    .andReturn().getResponse();
        } finally { DatabaseBarrier.active.remove(); }
    }

    private static class InsertBarrier {
        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch resume = new CountDownLatch(1);
        void beforeInsert(String sql) throws InterruptedException {
            if (sql != null && sql.startsWith("insert into program_rates ")) {
                ready.countDown();
                if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Rating insert barrier timed out");
            }
        }
    }

    /** Coordination at the external JDBC boundary; SQL still executes against the actual database. */
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseBarrier {
        static final ThreadLocal<InsertBarrier> active = new ThreadLocal<>();
        @Bean static BeanPostProcessor ratingInsertBarrier() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof DataSource source ? wrap(DataSource.class, source, null) : bean;
                }
            };
        }
        private static Object wrap(Class<?> type, Object target, String sql) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getName().startsWith("execute") && active.get() != null) active.get().beforeInsert(sql);
                Object result;
                try { result = method.invoke(target, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
                if (result instanceof Connection) return wrap(Connection.class, result, null);
                if (result instanceof PreparedStatement && args != null && args[0] instanceof String query)
                    return wrap(PreparedStatement.class, result, query);
                return result;
            });
        }
    }
}
