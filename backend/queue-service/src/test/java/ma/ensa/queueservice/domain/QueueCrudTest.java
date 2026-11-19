package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import ma.ensa.queueservice.web.Views.EstablishmentCreateRequest;
import ma.ensa.queueservice.web.Views.EstablishmentUpdateRequest;
import ma.ensa.queueservice.web.Views.QueueRequest;
import ma.ensa.queueservice.web.Views.ScheduleRequest;
import ma.ensa.queueservice.web.Views.WeeklyHoursRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueCrudTest {

    private EstablishmentRepository establishments;
    private ServiceQueueRepository queues;
    private TicketRepository tickets;
    private WeeklyHoursRepository hours;
    private QueueService service;
    private Establishment clinic;
    private Establishment agency;
    private ServiceQueue clinicQueue;
    private ServiceQueue agencyQueue;

    private final CurrentUser clinicAdmin = new CurrentUser("admin-1", "admin", Set.of("admin"), Set.of("clinic"));
    private final CurrentUser agencyAdmin = new CurrentUser("admin-2", "admin-agency", Set.of("admin"), Set.of("agency"));
    private final CurrentUser clinicAdminWithAnnex =
            new CurrentUser("admin-1", "admin", Set.of("admin"), Set.of("clinic", "annex"));
    private final CurrentUser clinicAgent = new CurrentUser("agent-1", "agent", Set.of("agent"), Set.of("clinic"));

    @BeforeEach
    void setUp() {
        establishments = mock(EstablishmentRepository.class);
        queues = mock(ServiceQueueRepository.class);
        tickets = mock(TicketRepository.class);
        hours = mock(WeeklyHoursRepository.class);
        service = new QueueService(establishments, queues, tickets, hours);

        clinic = new Establishment("clinic", "Clinique Al Amal", "Clinique", "Casablanca", "CA",
                "#41a98a", "#e0f4ec", "Rue Abou Alaa Zahar, Casablanca", "08:30", "16:30");
        agency = new Establishment("agency", "Agence Guéliz", "Administration", "Marrakech", "AG",
                "#df8d62", "#fff0e6", "Avenue Mohammed V, Marrakech", "08:30", "16:30");
        clinicQueue = new ServiceQueue(clinic, "Dépôt de dossier", "A", 3, "Guichet 3");
        ReflectionTestUtils.setField(clinicQueue, "id", 1L);
        agencyQueue = new ServiceQueue(agency, "Retrait de documents", "B", 2, "Guichet 1");
        ReflectionTestUtils.setField(agencyQueue, "id", 2L);

        when(establishments.findById("clinic")).thenReturn(Optional.of(clinic));
        when(establishments.findById("agency")).thenReturn(Optional.of(agency));
        when(queues.findByIdForUpdate(1L)).thenReturn(Optional.of(clinicQueue));
        when(queues.findByIdForUpdate(2L)).thenReturn(Optional.of(agencyQueue));
        when(queues.save(any(ServiceQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static EstablishmentUpdateRequest profile(String name, String address) {
        return new EstablishmentUpdateRequest(name, "Clinique", "Casablanca", "CA",
                "#41a98a", "#e0f4ec", address, "08:30", "16:30");
    }

    private static QueueRequest queue(String name, String prefix, int minutes, String counter) {
        return new QueueRequest(name, prefix, minutes, counter);
    }

    private static int statusOf(Runnable call) {
        return assertThrows(ResponseStatusException.class, call::run).getStatusCode().value();
    }

    @Test
    void clinicAdminUpdatesTheClinicProfile() {
        var view = service.updateEstablishment(clinicAdmin, "clinic", profile("Clinique Al Amal", "12 rue nouvelle"));

        assertEquals("12 rue nouvelle", view.address());
    }

    @Test
    void clinicAdminCannotUpdateTheAgency() {
        assertEquals(403, statusOf(() -> service.updateEstablishment(clinicAdmin, "agency", profile("Agence Guéliz", "x"))));
        assertEquals("Agence Guéliz", agency.getName());
    }

    @Test
    void agencyAdminCannotUpdateTheClinic() {
        assertEquals(403, statusOf(() -> service.updateEstablishment(agencyAdmin, "clinic", profile("Détournée", "x"))));
        assertEquals("Clinique Al Amal", clinic.getName());
    }

    @Test
    void clinicAdminSuspendsAndReactivatesTheClinic() {
        assertFalse(service.setEstablishmentActive(clinicAdmin, "clinic", false).active());
        assertTrue(service.setEstablishmentActive(clinicAdmin, "clinic", true).active());
    }

    @Test
    void clinicAdminCannotSuspendTheAgency() {
        assertEquals(403, statusOf(() -> service.setEstablishmentActive(clinicAdmin, "agency", false)));
        assertTrue(agency.isActive());
    }

    @Test
    void agencyAdminCannotSuspendTheClinic() {
        assertEquals(403, statusOf(() -> service.setEstablishmentActive(agencyAdmin, "clinic", false)));
        assertTrue(clinic.isActive());
    }

    @Test
    void clinicAdminCreatesAQueueInTheClinic() {
        var captor = org.mockito.ArgumentCaptor.forClass(ServiceQueue.class);

        var view = service.createQueue(clinicAdmin, "clinic", queue("Retrait", "C", 4, "Guichet 2"));

        verify(queues).save(captor.capture());
        assertEquals("Retrait", view.name());
        assertEquals("clinic", captor.getValue().getEstablishment().getId());
        assertEquals(4, captor.getValue().getAvgServiceMinutes());
    }

    @Test
    void clinicAdminCannotCreateAQueueInTheAgency() {
        assertEquals(403, statusOf(() -> service.createQueue(clinicAdmin, "agency", queue("Intruse", "X", 4, "G"))));
        verify(queues, never()).save(any(ServiceQueue.class));
    }

    @Test
    void agencyAdminCannotCreateAQueueInTheClinic() {
        assertEquals(403, statusOf(() -> service.createQueue(agencyAdmin, "clinic", queue("Intruse", "X", 4, "G"))));
        verify(queues, never()).save(any(ServiceQueue.class));
    }

    @Test
    void clinicAdminUpdatesItsOwnQueue() {
        var view = service.updateQueue(clinicAdmin, 1L, queue("Dépôt", "A", 5, "Guichet 3"));

        assertEquals("Dépôt", view.name());
        assertEquals(5, clinicQueue.getAvgServiceMinutes());
    }

    @Test
    void clinicAdminCannotUpdateTheAgencyQueue() {
        assertEquals(403, statusOf(() -> service.updateQueue(clinicAdmin, 2L, queue("Détournée", "B", 2, "G"))));
        assertEquals("Retrait de documents", agencyQueue.getName());
    }

    @Test
    void agencyAdminCannotUpdateTheClinicQueue() {
        assertEquals(403, statusOf(() -> service.updateQueue(agencyAdmin, 1L, queue("Détournée", "A", 3, "G"))));
        assertEquals("Dépôt de dossier", clinicQueue.getName());
    }

    @Test
    void clinicAdminCannotDeleteTheAgencyQueue() {
        assertEquals(403, statusOf(() -> service.deleteQueue(clinicAdmin, 2L)));
        verify(queues, never()).delete(any(ServiceQueue.class));
    }

    @Test
    void agencyAdminCannotDeleteTheClinicQueue() {
        assertEquals(403, statusOf(() -> service.deleteQueue(agencyAdmin, 1L)));
        verify(queues, never()).delete(any(ServiceQueue.class));
    }

    @Test
    void anAgentCannotUseTheAdminRoutes() {
        assertEquals(403, statusOf(() -> service.updateEstablishment(clinicAgent, "clinic", profile("Clinique Al Amal", "x"))));
        assertEquals(403, statusOf(() -> service.createQueue(clinicAgent, "clinic", queue("File", "F", 3, "G"))));
        assertEquals(403, statusOf(() -> service.deleteQueue(clinicAgent, 1L)));
    }

    @Test
    void anEstablishmentIsOnlyCreatedForASiteTheCallerHolds() {
        EstablishmentCreateRequest request = new EstablishmentCreateRequest("annex", "Annexe", "Clinique",
                "Casablanca", "AX", null, null, "Rue 2", "08:30", "16:30");

        assertEquals(403, statusOf(() -> service.createEstablishment(clinicAdmin, request)));
        verify(establishments, never()).save(any(Establishment.class));
    }

    @Test
    void anExistingEstablishmentIdIsRefused() {
        when(establishments.existsById("annex")).thenReturn(true);
        EstablishmentCreateRequest request = new EstablishmentCreateRequest("annex", "Annexe", "Clinique",
                "Casablanca", "AX", null, null, "Rue 2", "08:30", "16:30");

        assertEquals(409, statusOf(() -> service.createEstablishment(clinicAdminWithAnnex, request)));
    }

    @Test
    void aNewSiteBecomesAnEstablishmentOfTheCallersOrganisation() {
        var captor = org.mockito.ArgumentCaptor.forClass(Establishment.class);
        when(establishments.save(any(Establishment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EstablishmentCreateRequest request = new EstablishmentCreateRequest("annex", "Annexe", "Clinique",
                "Casablanca", "AX", null, null, "Rue 2", "08:30", "16:30");

        var view = service.createEstablishment(clinicAdminWithAnnex, request);

        verify(establishments).save(captor.capture());
        assertEquals("annex", view.id());
        assertEquals("annex", captor.getValue().getOrganizationId());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    void aQueueWithTicketsCannotBeDeleted() {
        when(tickets.existsByQueueId(1L)).thenReturn(true);

        assertEquals(409, statusOf(() -> service.deleteQueue(clinicAdmin, 1L)));
        verify(queues, never()).delete(any(ServiceQueue.class));
    }

    @Test
    void anEmptyQueueIsDeleted() {
        when(tickets.existsByQueueId(1L)).thenReturn(false);

        service.deleteQueue(clinicAdmin, 1L);

        verify(queues).delete(clinicQueue);
    }

    @Test
    void clinicAdminReadsTheClinicSchedule() {
        when(hours.findByEstablishmentIdOrderByDayOfWeekAscOpensAtAsc("clinic")).thenReturn(List.of(
                new WeeklyHours(clinic, (short) 1, LocalTime.of(8, 30), LocalTime.of(12, 30)),
                new WeeklyHours(clinic, (short) 1, LocalTime.of(13, 30), LocalTime.of(16, 30))));

        var schedule = service.schedule(clinicAdmin, "clinic");

        assertEquals(1, schedule.get(0).dayOfWeek());
        assertEquals("08:30", schedule.get(0).opensAt());
        assertEquals("16:30", schedule.get(1).closesAt());
    }

    @Test
    void clinicAdminCannotReadOrWriteTheAgencySchedule() {
        assertEquals(403, statusOf(() -> service.schedule(clinicAdmin, "agency")));
        assertEquals(403, statusOf(() -> service.replaceSchedule(clinicAdmin, "agency",
                schedule(interval(1, "08:30", "16:30")))));
        verify(hours, never()).deleteByEstablishmentId(any());
        verify(hours, never()).saveAll(any());
    }

    @Test
    void agencyAdminCannotWriteTheClinicSchedule() {
        assertEquals(403, statusOf(() -> service.replaceSchedule(agencyAdmin, "clinic",
                schedule(interval(1, "08:30", "16:30")))));
        verify(hours, never()).deleteByEstablishmentId(any());
    }

    @Test
    void clinicAdminReplacesTheWholeClinicSchedule() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(hours.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.replaceSchedule(clinicAdmin, "clinic", schedule(
                interval(2, "14:00", "18:00"),
                interval(1, "08:30", "12:30"),
                interval(1, "13:30", "16:30")));

        verify(hours).deleteByEstablishmentId("clinic");
        verify(hours).saveAll(captor.capture());
        var entities = (List<WeeklyHours>) captor.getValue();
        assertEquals(List.of((short) 1, (short) 1, (short) 2),
                entities.stream().map(WeeklyHours::getDayOfWeek).toList());
        assertEquals("08:30", saved.get(0).opensAt());
    }

    @Test
    void overlappingIntervalsAreRefused() {
        assertEquals(400, statusOf(() -> service.replaceSchedule(clinicAdmin, "clinic", schedule(
                interval(1, "08:30", "12:30"),
                interval(1, "12:00", "16:30")))));
        verify(hours, never()).deleteByEstablishmentId(any());
    }

    @Test
    void backwardsIntervalsAreRefused() {
        assertEquals(400, statusOf(() -> service.replaceSchedule(clinicAdmin, "clinic",
                schedule(interval(1, "16:30", "08:30")))));
        verify(hours, never()).deleteByEstablishmentId(any());
    }

    @Test
    void anAgentCannotEditTheSchedule() {
        assertEquals(403, statusOf(() -> service.replaceSchedule(clinicAgent, "clinic",
                schedule(interval(1, "08:30", "16:30")))));
        verify(hours, never()).deleteByEstablishmentId(any());
    }

    private static ScheduleRequest schedule(WeeklyHoursRequest... intervals) {
        return new ScheduleRequest(List.of(intervals));
    }

    private static WeeklyHoursRequest interval(int day, String opensAt, String closesAt) {
        return new WeeklyHoursRequest(day, opensAt, closesAt);
    }
}
