package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V33 on a real PostgreSQL: size limits, and what the foreign keys do when a user, a school or a thread is deleted.
 * Needs a THROWAWAY database (it is wiped): same variables and safeguards as ExpertApprovalMigrationPostgresIntegrationTests;
 * skipped when MIGRATION_PG_URL is absent.
 */
class ForumMigrationPostgresIntegrationTests {

    @Test
    void authorsAndSchoolsAreDetachedAndRepliesFollowTheirThread() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";

        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('Awa', 'Diop', 'forum-author@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
            st.executeUpdate("insert into schools (name, code) values ('Forum school', 'FS001')");
            var authorId = one(st, "select id from users where email = 'forum-author@example.com'");
            var schoolId = one(st, "select id from schools where code = 'FS001'");

            st.executeUpdate("insert into forum_threads (title, body, author_id, school_id, created_at) values "
                    + "('t', 'b', " + authorId + ", " + schoolId + ", now())");
            var threadId = one(st, "select id from forum_threads");
            st.executeUpdate("insert into forum_replies (thread_id, body, author_id, created_at) values "
                    + "(" + threadId + ", 'r', " + authorId + ", now())");
            assertThat(one(st, "select count(*) from forum_threads where locked = false and hidden = false")).isEqualTo(1);

            assertThatThrownBy(() -> st.executeUpdate("insert into forum_threads (title, body, created_at) values ('"
                    + "x".repeat(151) + "', 'b', now())")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.executeUpdate("insert into forum_replies (thread_id, body, created_at) values ("
                    + threadId + ", '" + "x".repeat(5001) + "', now())")).isInstanceOf(SQLException.class);

            try (var rs = st.executeQuery("select indexdef from pg_indexes where indexname = 'idx_forum_threads_school'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("school filter index serves the newest-first order")
                        .contains("school_id, created_at DESC, id DESC");
            }

            st.executeUpdate("delete from schools where id = " + schoolId);
            assertThat(nullCount(st, "forum_threads", "school_id")).isEqualTo(1);

            st.executeUpdate("delete from users where id = " + authorId);
            assertThat(nullCount(st, "forum_threads", "author_id")).isEqualTo(1);
            assertThat(nullCount(st, "forum_replies", "author_id")).isEqualTo(1);

            st.executeUpdate("delete from forum_threads where id = " + threadId);
            assertThat(one(st, "select count(*) from forum_replies")).isZero();
        }
    }

    private static int one(Statement st, String sql) throws SQLException {
        try (var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int nullCount(Statement st, String table, String column) throws SQLException {
        return one(st, "select count(*) from " + table + " where " + column + " is null");
    }
}
