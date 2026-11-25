package ma.ensa.appointmentservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantBackfillTest {

    private final AppointmentRepository appointments = mock(AppointmentRepository.class);
    private final TenantBackfill backfill = new TenantBackfill(appointments);

    @Test
    void newAppointmentDefaultsToEstablishmentOrganisation() {
        Appointment appointment = appointment("client-1", "clinic", 1L);

        assertEquals("clinic", appointment.getOrganizationId());
    }

    @Test
    void fillsMissingOrganisationAndKeepsExistingValues() {
        Appointment legacy = appointment("client-1", "clinic", 1L);
        legacy.assignOrganizationId(null);
        Appointment tagged = appointment("client-2", "agency", 2L);
        tagged.assignOrganizationId("agency-site");

        when(appointments.findAll()).thenReturn(List.of(legacy, tagged));

        backfill.run(null);
        backfill.run(null);

        assertEquals("clinic", legacy.getOrganizationId());
        assertEquals("agency-site", tagged.getOrganizationId());
    }

    private Appointment appointment(String owner, String establishmentId, long queueId) {
        return new Appointment(owner, establishmentId, queueId,
                Instant.parse("2099-01-01T10:00:00Z"), "Consultation");
    }
}
