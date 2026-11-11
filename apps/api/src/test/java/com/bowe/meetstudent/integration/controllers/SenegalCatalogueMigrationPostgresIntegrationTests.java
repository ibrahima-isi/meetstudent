package com.bowe.meetstudent.integration.controllers;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SenegalCatalogueMigrationPostgresIntegrationTests {

    @Test
    void importsOfficialCatalogueIntoAnExistingDatabase() throws Exception {
        var url = System.getenv("MIGRATION_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "MIGRATION_PG_URL not set");
        ExpertApprovalMigrationPostgresIntegrationTests.requireThrowawayDatabase(url);
        var user = System.getenv().getOrDefault("MIGRATION_PG_USER", "postgres");
        var password = System.getenv().getOrDefault("MIGRATION_PG_PASSWORD", "postgres");
        var location = "filesystem:src/main/resources/db/migration";
        Flyway.configure().dataSource(url, user, password).locations(location).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, user, password).locations(location).target("37").load().migrate();

        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into schools (code, name, country, city)
                    values ('UGB', 'Université Gaston Berger de Saint-Louis', 'Sénégal', 'Saint-Louis');
                    insert into programs (school_id, name, description, level)
                    select id, 'Licence Communication', 'Description éditoriale', 'LICENCE'
                    from schools where code = 'UGB'
                    """);
        }

        var flyway = Flyway.configure().dataSource(url, user, password).locations(location).load();
        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            assertThat(count(statement, "select count(*) from schools where source_url is not null")).isEqualTo(99);
            assertThat(count(statement, "select count(*) from programs where source_url is not null")).isEqualTo(1222);
            assertThat(count(statement, "select count(*) from schools where campusen_id is not null")).isEqualTo(94);
            assertThat(count(statement, "select count(*) from programs where campusen_id is not null")).isEqualTo(1171);
            assertThat(count(statement, "select count(*) from schools where campusen_id = 2 and code = 'UGB' and city = 'Saint-Louis'")).isEqualTo(1);
            assertThat(count(statement, """
                    select count(*) from programs p join schools s on p.school_id = s.id
                    where p.campusen_id = 1331 and s.campusen_id = 2 and p.description = 'Description éditoriale'
                    """)).isEqualTo(1);
            assertThat(count(statement, "select count(*) from courses where source_url like 'https://www.ugb.sn/%'")).isEqualTo(274);
            assertThat(count(statement, """
                    select count(*) from courses c join programs p on c.program_id = p.id
                    where p.campusen_id = 1331
                      and c.name = 'Théories des sciences de l’information et de la communication'
                      and c.source_url like '%CRAC-Maquette-Licence-COM.pdf#page=1'
                    """)).isEqualTo(1);
            assertThat(count(statement, "select count(*) from program_accreditations where source_url is not null")).isEqualTo(41);
            assertThat(count(statement, """
                    select count(distinct a.code) from program_accreditations pa
                    join accreditations a on a.id = pa.accreditation_id
                    where pa.source_url is not null and pa.starts between 2022 and 2026 and pa.ends = pa.starts + 5
                    """)).isEqualTo(2);
            assertThat(count(statement, """
                    select count(*) from school_tags st join tags t on st.tag_id = t.id
                    where t.name = 'Habilitation MESRI documentée'
                    """)).isEqualTo(10);
            assertThat(count(statement, """
                    select count(*) from school_habilitations h join schools s on s.id = h.school_id
                    where s.campusen_id = 12 and h.reference = 'RepSEN/Ensup-priv/HA/010-2017'
                    """)).isEqualTo(1);
        }
    }

    private static int count(Statement statement, String query) throws Exception {
        try (var rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }
}
