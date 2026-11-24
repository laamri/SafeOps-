package ma.ensa.queueservice.domain;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2 adds the establishment suspension flag and the weekly opening calendar. The rules are in
 * the migration, so they must hold for rows written outside the API too.
 */
class WeeklyHoursSchemaTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");
    private static Connection connection;

    @BeforeAll
    static void migrateSchema() throws Exception {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO establishment (id, name) VALUES ('clinic', 'Clinique')");
        }
    }

    @AfterAll
    static void stop() throws Exception {
        if (connection != null) {
            connection.close();
        }
        POSTGRES.stop();
    }

    @Test
    void freshDatabaseGetsTheTableAndTheFlag() throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery(
                     "SELECT column_name, is_nullable, column_default FROM information_schema.columns"
                             + " WHERE table_name = 'weekly_hours' ORDER BY ordinal_position")) {
            StringBuilder names = new StringBuilder();
            while (columns.next()) {
                names.append(columns.getString("column_name")).append(',');
            }
            assertEquals("id,establishment_id,day_of_week,opens_at,closes_at,", names.toString());
        }

        try (Statement statement = connection.createStatement();
             ResultSet active = statement.executeQuery(
                     "SELECT active FROM establishment WHERE id = 'clinic'")) {
            assertTrue(active.next());
            assertTrue(active.getBoolean(1));
        }
    }

    @Test
    void twoIntervalsOnOneDayAreAllowed() throws Exception {
        insertInterval("clinic", 1, "08:30", "12:30");
        insertInterval("clinic", 1, "13:30", "16:30");

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT count(*) FROM weekly_hours WHERE establishment_id = 'clinic' AND day_of_week = 1")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void invalidRowsAreRejectedByTheDatabase() {
        SQLException badDay = assertThrows(SQLException.class,
                () -> insertInterval("clinic", 8, "08:30", "16:30"));
        assertEquals("23514", badDay.getSQLState());

        SQLException backwards = assertThrows(SQLException.class,
                () -> insertInterval("clinic", 2, "16:30", "08:30"));
        assertEquals("23514", backwards.getSQLState());

        SQLException unknownSite = assertThrows(SQLException.class,
                () -> insertInterval("nowhere", 3, "08:30", "16:30"));
        assertEquals("23503", unknownSite.getSQLState());
    }

    @Test
    void deletingAnEstablishmentRemovesItsIntervals() throws Exception {
        insertInterval("clinic", 4, "08:30", "16:30");
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM establishment WHERE id = 'clinic'");
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM weekly_hours")) {
            assertTrue(rows.next());
            assertFalse(rows.getInt(1) > 0);
        }
        // later tests in this class do not depend on the establishment row
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO establishment (id, name) VALUES ('clinic', 'Clinique')");
        }
    }

    private void insertInterval(String establishmentId, int day, String opensAt, String closesAt)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO weekly_hours (establishment_id, day_of_week, opens_at, closes_at)"
                        + " VALUES (?, ?, CAST(? AS time), CAST(? AS time))")) {
            ps.setString(1, establishmentId);
            ps.setInt(2, day);
            ps.setString(3, opensAt);
            ps.setString(4, closesAt);
            ps.executeUpdate();
        }
    }
}
