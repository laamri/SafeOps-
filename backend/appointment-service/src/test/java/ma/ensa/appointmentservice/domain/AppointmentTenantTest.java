package ma.ensa.appointmentservice.domain;

import ma.ensa.appointmentservice.security.CurrentUser;
import ma.ensa.appointmentservice.web.Views.CreateAppointmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentTenantTest {

    private AppointmentRepository appointments;
    private AppointmentService service;

    private final CurrentUser clinicAgent = new CurrentUser("agent-1", "agent", Set.of("agent"), Set.of("clinic"));
    private final CurrentUser agencyAgent = new CurrentUser("agent-2", "agent-agency", Set.of("agent"), Set.of("agency"));
    private final CurrentUser clinicAdmin = new CurrentUser("admin-1", "admin", Set.of("admin"), Set.of("clinic"));
    private final CurrentUser staffWithoutSite = new CurrentUser("agent-3", "agent-nogroup", Set.of("agent"), Set.of());
    private final CurrentUser client = new CurrentUser("client-1", "client", Set.of("client"), Set.of());

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentRepository.class);
        service = new AppointmentService(appointments, "clinic,agency");
    }

    @Test
    void clinicStaffListClinicAppointments() {
        when(appointments.findByEstablishmentIdOrderByStartsAtAsc("clinic"))
                .thenReturn(List.of(clinicAppointment()));

        assertEquals(1, service.byEstablishment(clinicAgent, "clinic").size());
    }

    @Test
    void clinicStaffCannotListAgencyAppointments() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.byEstablishment(clinicAgent, "agency"));

        assertEquals(403, exception.getStatusCode().value());
        verify(appointments, never()).findByEstablishmentIdOrderByStartsAtAsc("agency");
    }

    @Test
    void agencyStaffCannotListClinicAppointments() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.byEstablishment(agencyAgent, "clinic"));

        assertEquals(403, exception.getStatusCode().value());
        verify(appointments, never()).findByEstablishmentIdOrderByStartsAtAsc("clinic");
    }

    @Test
    void aStaffTokenWithoutASiteClaimIsRefused() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.byEstablishment(staffWithoutSite, "clinic"));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void anAdminIsNotABypass() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.byEstablishment(clinicAdmin, "agency"));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void clinicStaffCompleteAClinicAppointment() {
        Appointment appointment = clinicAppointment();
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertEquals(AppointmentStatus.COMPLETED, service.complete(clinicAgent, appointment.getId()).status());
    }

    @Test
    void clinicStaffCannotCompleteAnAgencyAppointment() {
        Appointment appointment = agencyAppointment();
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.complete(clinicAgent, appointment.getId()));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void agencyStaffCannotNoShowAClinicAppointment() {
        Appointment appointment = clinicAppointment();
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.noShow(agencyAgent, appointment.getId()));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
    }

    @Test
    void agencyStaffNoShowAnAgencyAppointment() {
        Appointment appointment = agencyAppointment();
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertEquals(AppointmentStatus.NO_SHOW, service.noShow(agencyAgent, appointment.getId()).status());
    }

    @Test
    void noSiteStaffCannotCompleteAnything() {
        Appointment appointment = clinicAppointment();
        when(appointments.findById(appointment.getId())).thenReturn(Optional.of(appointment));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.complete(staffWithoutSite, appointment.getId()));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void clientsStillBookAtAnyKnownEstablishment() {
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(client, new CreateAppointmentRequest(
                "agency", 3L, Instant.parse("2099-01-01T10:00:00Z"), "Document appointment"));

        assertEquals("agency", result.establishmentId());
        assertEquals("agency", result.organizationId());
        assertEquals(AppointmentStatus.BOOKED, result.status());
    }

    @Test
    void anUnknownEstablishmentIsRejected() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.create(client, new CreateAppointmentRequest(
                        "elsewhere", 1L, Instant.parse("2099-01-01T10:00:00Z"), "Consultation")));

        assertEquals(400, exception.getStatusCode().value());
        verify(appointments, never()).save(any(Appointment.class));
    }

    @Test
    void anUnknownEstablishmentIsRejectedForStaffListingToo() {
        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.byEstablishment(clinicAgent, "elsewhere"));

        assertEquals(400, exception.getStatusCode().value());
    }

    private Appointment clinicAppointment() {
        return new Appointment("client-1", "clinic", 1L,
                Instant.parse("2099-01-01T10:00:00Z"), "Consultation");
    }

    private Appointment agencyAppointment() {
        return new Appointment("client-1", "agency", 3L,
                Instant.parse("2099-01-01T10:00:00Z"), "Document appointment");
    }
}
