package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V37 on a real PostgreSQL: refresh_tokens gains the nullable previous_token (hash of the secret just rotated away,
 * indexed for the replay lookup) and rotated_at; sessions issued before it survive unchanged with neither set.
 * Needs a THROWAWAY database (it is wiped): same variables and safeguards as ExpertApprovalMigrationPostgresIntegrationTests;
 * skipped when MIGRATION_PG_URL is absent.
 */
class RefreshTokenReuseMigrationPostgresIntegrationTests {

    @Test
    void v37AddsTheNullablePreviousHashAndRotationTimeWithoutTouchingExistingSessions() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).target("36").load().migrate();

        var hash = AuthIntegrationTests.sha256Hex("existing-session");
        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('Live', 'Session', 'live-session@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
            st.executeUpdate("insert into refresh_tokens (token, expiry_date, user_id, last_activity) values ('" + hash
                    + "', now() + interval '7 days', (select id from users where email = 'live-session@example.com'), now())");
        }

        Flyway.configure().dataSource(url, user, password).locations(location).load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            try (var rows = st.executeQuery("select column_name, data_type, character_maximum_length, is_nullable "
                    + "from information_schema.columns where table_name = 'refresh_tokens' "
                    + "and column_name in ('previous_token', 'rotated_at') order by column_name")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("previous_token");
                assertThat(rows.getString(2)).isEqualTo("character varying");
                assertThat(rows.getInt(3)).isEqualTo(255);
                assertThat(rows.getString(4)).isEqualTo("YES");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("rotated_at");
                assertThat(rows.getString(2)).isEqualTo("timestamp with time zone");
                assertThat(rows.getString(4)).isEqualTo("YES");
            }
            try (var rows = st.executeQuery("select indexdef from pg_indexes where tablename = 'refresh_tokens' "
                    + "and indexdef like '%(previous_token)%'")) {
                assertThat(rows.next()).as("index on previous_token").isTrue();
            }
            try (var rows = st.executeQuery("select r.token, r.previous_token, r.rotated_at from refresh_tokens r "
                    + "join users u on u.id = r.user_id where u.email = 'live-session@example.com'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(hash);
                assertThat(rows.getString(2)).isNull();
                assertThat(rows.getObject(3)).isNull();
            }
        }
    }
}
