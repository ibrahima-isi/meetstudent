package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V37 on a real PostgreSQL: refresh_token_history keeps the hashes a session rotated away (token_hash UNIQUE,
 * NOT NULL rotated_at, ON DELETE CASCADE from refresh_tokens, indexed for the per-session lookups and the purge);
 * sessions issued before it survive unchanged with an empty history.
 * Needs a THROWAWAY database (it is wiped): same variables and safeguards as ExpertApprovalMigrationPostgresIntegrationTests;
 * skipped when MIGRATION_PG_URL is absent.
 */
class RefreshTokenReuseMigrationPostgresIntegrationTests {

    @Test
    void v37CreatesTheRotatedHashHistoryWithoutTouchingExistingSessions() throws Exception {
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
            try (var rows = st.executeQuery("select token from refresh_tokens r join users u on u.id = r.user_id "
                    + "where u.email = 'live-session@example.com'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(hash);
            }
            try (var rows = st.executeQuery("select count(*) from refresh_token_history")) {
                rows.next();
                assertThat(rows.getInt(1)).as("nothing backfilled").isZero();
            }
            try (var rows = st.executeQuery("select column_name, data_type, character_maximum_length, is_nullable "
                    + "from information_schema.columns where table_name = 'refresh_token_history' order by column_name")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("id");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("refresh_token_id");
                assertThat(rows.getString(4)).isEqualTo("NO");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("rotated_at");
                assertThat(rows.getString(2)).isEqualTo("timestamp with time zone");
                assertThat(rows.getString(4)).isEqualTo("NO");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("token_hash");
                assertThat(rows.getString(2)).isEqualTo("character varying");
                assertThat(rows.getInt(3)).isEqualTo(64);
                assertThat(rows.getString(4)).isEqualTo("NO");
                assertThat(rows.next()).isFalse();
            }
            try (var rows = st.executeQuery("select string_agg(indexdef, ' | ') from pg_indexes where tablename = 'refresh_token_history'")) {
                rows.next();
                assertThat(rows.getString(1)).contains("(refresh_token_id)").contains("(rotated_at)")
                        .containsPattern("UNIQUE INDEX \\S+ ON \\S+ USING btree \\(token_hash\\)");
            }

            var session = "(select id from refresh_tokens where token = '" + hash + "')";
            st.executeUpdate("insert into refresh_token_history (refresh_token_id, token_hash, rotated_at) values ("
                    + session + ", '" + AuthIntegrationTests.sha256Hex("older") + "', now())");
            assertThatThrownBy(() -> st.executeUpdate("insert into refresh_token_history (refresh_token_id, token_hash, rotated_at) values ("
                    + session + ", '" + AuthIntegrationTests.sha256Hex("older") + "', now())"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23505"));
            assertThatThrownBy(() -> st.executeUpdate("insert into refresh_token_history (refresh_token_id, token_hash, rotated_at) values ("
                    + session + ", '" + "a".repeat(65) + "', now())"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("22001"));

            // Ending a session (logout, reuse, credential change, account deletion) takes its history with it.
            st.executeUpdate("delete from refresh_tokens where token = '" + hash + "'");
            try (var rows = st.executeQuery("select count(*) from refresh_token_history")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
