package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V36 on a real PostgreSQL: program ratings without a program are deleted, the valid ones survive, and the column
 * refuses NULL afterwards. Needs a THROWAWAY database (it is wiped): same variables and safeguards as
 * ExpertApprovalMigrationPostgresIntegrationTests; skipped when MIGRATION_PG_URL is absent.
 */
class ProgramRatesRequireProgramMigrationPostgresIntegrationTests {

    @Test
    void v36DeletesOrphanProgramRatesAndForbidsNewOnes() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).target("35").load().migrate();

        var userId = "(select id from users where email = 'orphan-rater@example.com')";
        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('Orphan', 'Rater', 'orphan-rater@example.com', 'x', (select id from roles where name = 'ROLE_EXPERT'), now(), now())");
            st.executeUpdate("insert into programs (name) values ('V36 program')");
            st.executeUpdate("insert into program_rates (note, user_id, program_id) values (5, " + userId + ", null)");
            st.executeUpdate("insert into program_rates (note, user_id, program_id) values (5, " + userId + ", null)");
            st.executeUpdate("insert into program_rates (note, user_id, program_id) values (4, " + userId
                    + ", (select id from programs where name = 'V36 program'))");
        }

        Flyway.configure().dataSource(url, user, password).locations(location).load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            var rows = st.executeQuery("select count(*) filter (where program_id is null), count(*) from program_rates");
            rows.next();
            assertThat(rows.getInt(1)).isZero();
            assertThat(rows.getInt(2)).isEqualTo(1);
            assertThatThrownBy(() -> st.executeUpdate("insert into program_rates (note, user_id, program_id) values (5, "
                    + userId + ", null)"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23502"));
        }
    }
}
