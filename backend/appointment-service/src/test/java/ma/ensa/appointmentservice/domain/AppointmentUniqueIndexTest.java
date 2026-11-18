package ma.ensa.appointmentservice.domain;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The slot rule is enforced by the partial unique index in the V1 migration, not by the service
 * checks alone, so it must hold even for rows written outside the API.
 */
class AppointmentUniqueIndexTest {

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
    }

    @AfterAll
    static void stop() throws Exception {
        if (connection != null) {
            connection.close();
        }
        POSTGRES.stop();
    }

    @Test
    void secondBookedAppointmentAtTheSameTimeIsRejected() throws Exception {
        Instant startsAt = Instant.parse("2026-12-01T09:00:00Z");
        insert("owner-conflict", startsAt, "BOOKED");

        SQLException conflict = assertThrows(SQLException.class,
                () -> insert("owner-conflict", startsAt, "BOOKED"));

        assertEquals("23505", conflict.getSQLState());
    }

    @Test
    void cancellingTheFirstBookedAppointmentFreesTheSlot() throws Exception {
        Instant startsAt = Instant.parse("2026-12-02T09:00:00Z");
        UUID first = insert("owner-cancel", startsAt, "BOOKED");

        try (PreparedStatement cancel = connection.prepareStatement(
                "UPDATE appointment SET status = 'CANCELLED' WHERE id = ?")) {
            cancel.setObject(1, first);
            cancel.executeUpdate();
        }

        assertDoesNotThrow(() -> insert("owner-cancel", startsAt, "BOOKED"));
        assertEquals(1, countBooked("owner-cancel", startsAt));
    }

    private UUID insert(String owner, Instant startsAt, String status) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO appointment (id, created_at, establishment_id, owner_subject, queue_id, reason,"
                        + " starts_at, status, organization_id)"
                        + " VALUES (?, now(), 'clinic', ?, 1, 'index test', ?, ?, 'clinic')")) {
            ps.setObject(1, id);
            ps.setString(2, owner);
            ps.setTimestamp(3, Timestamp.from(startsAt));
            ps.setString(4, status);
            ps.executeUpdate();
        }
        return id;
    }

    private int countBooked(String owner, Instant startsAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT count(*) FROM appointment WHERE owner_subject = ? AND starts_at = ? AND status = 'BOOKED'")) {
            ps.setString(1, owner);
            ps.setTimestamp(2, Timestamp.from(startsAt));
            try (var rows = ps.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }
}
