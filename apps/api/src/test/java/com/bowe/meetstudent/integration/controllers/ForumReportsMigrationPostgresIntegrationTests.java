package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V34 on a real PostgreSQL: exactly one target per report, one report per reporter and message, size and status
 * limits (checked by SQLSTATE, so a failure for another reason does not pass), what the foreign keys do when a
 * reporter, a handler, a reply or a thread is deleted, and the row-lock behaviour the lock/reply race relies on.
 * Needs a THROWAWAY database (it is wiped): same variables and safeguards as ExpertApprovalMigrationPostgresIntegrationTests;
 * skipped when MIGRATION_PG_URL is absent.
 */
class ForumReportsMigrationPostgresIntegrationTests {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String VALUE_TOO_LONG = "22001";

    private static String url;
    private static String user;
    private static String password;

    private static void migrate() {
        url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set: no throwaway PostgreSQL available");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).load().migrate();
    }

    @Test
    void constraintsAndCascadesOfForumReports() throws Exception {
        migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            for (var n : new String[] {"a", "b", "c", "admin"}) {
                st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                        + "('" + n + "', 'X', 'rep-" + n + "@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
            }
            var a = one(st, "select id from users where email = 'rep-a@example.com'");
            var b = one(st, "select id from users where email = 'rep-b@example.com'");
            var c = one(st, "select id from users where email = 'rep-c@example.com'");
            var admin = one(st, "select id from users where email = 'rep-admin@example.com'");
            st.executeUpdate("insert into forum_threads (title, body, author_id, created_at) values ('t', 'b', " + a + ", now())");
            var thread = one(st, "select id from forum_threads");
            st.executeUpdate("insert into forum_replies (thread_id, body, author_id, created_at) values (" + thread + ", 'r', " + a + ", now())");
            var reply = one(st, "select id from forum_replies");

            st.executeUpdate(report("thread_id", thread, b, "spam", "s"));
            assertThat(one(st, "select count(*) from forum_reports where status = 'OPEN' and handled_at is null and handled_by is null")).isEqualTo(1);

            // exactly one target
            assertSqlState(st, "insert into forum_reports (thread_id, reply_id, reporter_id, reason, target_snapshot, created_at) values ("
                    + thread + ", " + reply + ", " + c + ", 'both', 's', now())", CHECK_VIOLATION);
            assertSqlState(st, "insert into forum_reports (reporter_id, reason, target_snapshot, created_at) values ("
                    + c + ", 'none', 's', now())", CHECK_VIOLATION);
            // limits and status
            assertSqlState(st, report("thread_id", thread, c, "x".repeat(256), "s"), VALUE_TOO_LONG);
            assertSqlState(st, report("thread_id", thread, c, "ok", "x".repeat(1001)), VALUE_TOO_LONG);
            assertSqlState(st, "insert into forum_reports (thread_id, reporter_id, reason, status, target_snapshot, created_at) values ("
                    + thread + ", " + c + ", 'x', 'WHATEVER', 's', now())", CHECK_VIOLATION);
            st.executeUpdate(report("thread_id", thread, c, "x".repeat(255), "y".repeat(1000))); // the boundaries are accepted
            st.executeUpdate("delete from forum_reports where reporter_id = " + c);
            // one report per reporter and message (thread and reply targets separately)
            assertSqlState(st, report("thread_id", thread, b, "again", "s"), UNIQUE_VIOLATION);
            st.executeUpdate(report("reply_id", reply, b, "on reply", "s"));
            assertSqlState(st, report("reply_id", reply, b, "again", "s"), UNIQUE_VIOLATION);
            st.executeUpdate(report("thread_id", thread, c, "other reporter", "s"));
            assertThat(one(st, "select count(*) from forum_reports")).isEqualTo(3);

            try (var rs = st.executeQuery("select indexdef from pg_indexes where indexname = 'idx_forum_reports_status_created'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("status, created_at DESC, id DESC");
            }

            // closing records the administrator; deleting that account keeps the report
            st.executeUpdate("update forum_reports set status = 'HANDLED', handled_at = now(), handled_by = " + admin + " where reporter_id = " + c);
            st.executeUpdate("delete from users where id = " + admin);
            assertThat(one(st, "select count(*) from forum_reports where status = 'HANDLED' and handled_by is null")).isEqualTo(1);

            // deleting a reporter keeps his reports, anonymous (and several anonymous reports can coexist)
            st.executeUpdate("delete from users where id = " + b);
            assertThat(one(st, "select count(*) from forum_reports where reporter_id is null")).isEqualTo(2);
            assertThat(one(st, "select count(*) from forum_reports")).isEqualTo(3);

            // deleting the THREAD removes its replies and every report, on the thread and on the replies
            st.executeUpdate("delete from forum_threads where id = " + thread);
            assertThat(one(st, "select count(*) from forum_replies")).isZero();
            assertThat(one(st, "select count(*) from forum_reports")).isZero();
        }
    }

    @Test
    void deletingAReplyRemovesItsReportsAndLeavesTheThreadOnes() throws Exception {
        migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var st = connection.createStatement()) {
            st.executeUpdate("insert into users (firstname, lastname, email, password, role_id, created_at, modified_at) values "
                    + "('a', 'X', 'only@example.com', 'x', (select id from roles where name = 'ROLE_STUDENT'), now(), now())");
            var u = one(st, "select id from users where email = 'only@example.com'");
            st.executeUpdate("insert into forum_threads (title, body, created_at) values ('t', 'b', now())");
            var thread = one(st, "select id from forum_threads");
            st.executeUpdate("insert into forum_replies (thread_id, body, created_at) values (" + thread + ", 'r', now())");
            var reply = one(st, "select id from forum_replies");
            st.executeUpdate(report("thread_id", thread, u, "on thread", "s"));
            st.executeUpdate(report("reply_id", reply, u, "on reply", "s"));
            st.executeUpdate("delete from forum_replies where id = " + reply);
            assertThat(one(st, "select count(*) from forum_reports where reply_id is not null")).isZero();
            assertThat(one(st, "select count(*) from forum_reports where thread_id is not null")).isEqualTo(1);
        }
    }

    /**
     * The lock/reply race relies on SELECT ... FOR UPDATE making a second reader of the same thread row wait for the
     * first transaction and then see its committed state. Checked on the real engine with the statement Hibernate emits.
     */
    @Test
    void selectForUpdateMakesASecondTransactionWaitAndSeeTheCommittedLock() throws Exception {
        migrate();
        try (var setup = DriverManager.getConnection(url, user, password); var st = setup.createStatement()) {
            st.executeUpdate("insert into forum_threads (title, body, created_at) values ('t', 'b', now())");
        }
        try (var admin = DriverManager.getConnection(url, user, password);
             var replier = DriverManager.getConnection(url, user, password);
             var monitor = DriverManager.getConnection(url, user, password);
             var pool = new AutoCloseableExecutor()) {
            admin.setAutoCommit(false);
            replier.setAutoCommit(false);
            int thread;
            try (var st = admin.createStatement()) {
                thread = one(st, "select id from forum_threads");
                one(st, "select id from forum_threads where id = " + thread + " for update");
                st.executeUpdate("update forum_threads set locked = true where id = " + thread);
            }
            var seen = pool.executor.submit(() -> {
                try (var st = replier.createStatement(); var rs = st.executeQuery(
                        "select locked from forum_threads where id = " + thread + " for update")) {
                    rs.next();
                    var locked = rs.getBoolean(1);
                    replier.commit();
                    return locked;
                }
            });
            try (var st = monitor.createStatement()) {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (one(st, "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'") == 0
                        && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertThat(one(st, "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'"))
                        .as("the second transaction waits on the row lock").isEqualTo(1);
            }
            assertThat(seen.isDone()).isFalse();
            admin.commit();
            assertThat(seen.get(10, TimeUnit.SECONDS)).as("the waiting transaction sees the committed lock").isTrue();
        }
    }

    private static String report(String targetColumn, int target, int reporter, String reason, String snapshot) {
        return "insert into forum_reports (" + targetColumn + ", reporter_id, reason, target_snapshot, created_at) values ("
                + target + ", " + reporter + ", '" + reason + "', '" + snapshot + "', now())";
    }

    private static void assertSqlState(Statement st, String sql, String sqlState) {
        assertThatThrownBy(() -> st.executeUpdate(sql)).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).as(e.getMessage()).isEqualTo(sqlState));
    }

    private static int one(Statement st, String sql) throws SQLException {
        try (var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static final class AutoCloseableExecutor implements AutoCloseable {
        final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
