package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V29 on a real PostgreSQL: experts that exist before the migration become APPROVED, other roles get no status.
 * Needs a THROWAWAY database (it is wiped): set MIGRATION_PG_URL, MIGRATION_PG_USER, MIGRATION_PG_PASSWORD
 * (for instance a disposable postgres container, see apps/api/CLAUDE.md); skipped when MIGRATION_PG_URL is absent.
 * URLs on port 5432 (explicit or implied) or naming "meetstudent" are refused.
 */
class ExpertApprovalMigrationPostgresIntegrationTests {

    @Test
    void existingExpertsBecomeApprovedAndOtherRolesKeepNoStatus() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");

        Flyway.configure().dataSource(url, user, password).locations("filesystem:src/main/resources/db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations("filesystem:src/main/resources/db/migration").target("28").load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('Old', 'Expert', 'old-expert@example.com', 'x', (select id from roles where name = 'ROLE_EXPERT'), now(), now()),"
                    + "('Stu', 'Dent', 'student@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
        }

        Flyway.configure().dataSource(url, user, password).locations("filesystem:src/main/resources/db/migration").load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            assertThat(statusOf(st, "old-expert@example.com")).isEqualTo("APPROVED");
            assertThat(statusOf(st, "student@example.com")).isNull();
            assertThat(statusOf(st, "admin@meetstudent.com")).isNull();
            try (var rs = st.executeQuery("select count(*) from users where expert_status is not null and role_id <> "
                    + "(select id from roles where name = 'ROLE_EXPERT')")) {
                rs.next();
                assertThat(rs.getInt(1)).as("non-experts with a status").isZero();
            }
        }
    }

    /** The test wipes its database: never accept the default Postgres port or anything named after the project. */
    static void requireThrowawayDatabase(String url) {
        var explicitPort = java.util.regex.Pattern.compile("//[^/:]+:(\\d+)").matcher(url);
        var port = explicitPort.find() ? explicitPort.group(1) : "5432"; // no port means the default one
        if ("5432".equals(port) || url.toLowerCase().contains("meetstudent")) {
            throw new IllegalStateException("Refusing to wipe " + url + ": use a disposable container on another port");
        }
    }

    @Test
    void refusesAUrlThatCouldBeARealDatabase() {
        for (var url : new String[] {
                "jdbc:postgresql://localhost:5432/mig", "jdbc:postgresql://db/prod:5432", "jdbc:postgresql://localhost:55432/meetstudent",
                "jdbc:postgresql://localhost/mig", "jdbc:postgresql://localhost:55432/MeetStudent"}) {
            assertThatThrownBy(() -> requireThrowawayDatabase(url)).as(url).isInstanceOf(IllegalStateException.class);
        }
        requireThrowawayDatabase("jdbc:postgresql://127.0.0.1:55432/mig");
    }

    private static String statusOf(java.sql.Statement st, String email) throws Exception {
        try (var rs = st.executeQuery("select expert_status from users where email = '" + email + "'")) {
            assertThat(rs.next()).as("user %s exists", email).isTrue();
            return rs.getString(1);
        }
    }
}
