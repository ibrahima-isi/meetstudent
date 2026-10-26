package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V35 on a real PostgreSQL: refresh tokens issued before it are replaced by their SHA-256 (lowercase hex, the same
 * value the service computes), so the sessions survive the deployment and the clear values are gone.
 * Needs a THROWAWAY database (it is wiped): same variables and safeguards as ExpertApprovalMigrationPostgresIntegrationTests;
 * skipped when MIGRATION_PG_URL is absent.
 */
class RefreshTokenHashMigrationPostgresIntegrationTests {

    @Test
    void v35ReplacesEveryRefreshTokenByItsSha256() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).target("34").load().migrate();

        var clear = "4f1c2a9e-0b7d-4c55-9a51-3e2f8d6b7c10";
        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('Old', 'Session', 'old-session@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
            st.executeUpdate("insert into refresh_tokens (token, expiry_date, user_id, last_activity) values ('" + clear
                    + "', now() + interval '7 days', (select id from users where email = 'old-session@example.com'), now())");
        }

        Flyway.configure().dataSource(url, user, password).locations(location).load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            var rows = st.executeQuery("select r.token from refresh_tokens r join users u on u.id = r.user_id "
                    + "where u.email = 'old-session@example.com' and not r.legacy");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(AuthIntegrationTests.sha256Hex(clear));
            var leftovers = st.executeQuery("select count(*) from refresh_tokens where token !~ '^[0-9a-f]{64}$'");
            leftovers.next();
            assertThat(leftovers.getInt(1)).isZero();
        }
    }
}
