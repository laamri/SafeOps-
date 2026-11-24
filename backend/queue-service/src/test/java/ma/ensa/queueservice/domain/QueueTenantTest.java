package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

class QueueTenantTest {

    private ServiceQueueRepository queues;
    private TicketRepository tickets;
    private QueueService service;
    private ServiceQueue clinicQueue;
    private ServiceQueue agencyQueue;

    private final CurrentUser clinicAgent = new CurrentUser("agent-1", "agent", Set.of("agent"), Set.of("clinic"));
    private final CurrentUser agencyAgent = new CurrentUser("agent-2", "agent-agency", Set.of("agent"), Set.of("agency"));
    private final CurrentUser clinicAdmin = new CurrentUser("admin-1", "admin", Set.of("admin"), Set.of("clinic"));
    private final CurrentUser staffWithoutSite = new CurrentUser("agent-3", "agent-nogroup", Set.of("agent"), Set.of());
    private final CurrentUser client = new CurrentUser("client-1", "client", Set.of("client"), Set.of());

    @BeforeEach
    void setUp() {
        EstablishmentRepository establishments = mock(EstablishmentRepository.class);
        queues = mock(ServiceQueueRepository.class);
        tickets = mock(TicketRepository.class);
        service = new QueueService(establishments, queues, tickets);

        Establishment clinic = new Establishment("clinic", "Clinique Al Amal", "Clinique", "Casablanca", "CA",
                "#41a98a", "#e0f4ec", "Rue Abou Alaa Zahar, Casablanca", "08:30", "16:30");
        Establishment agency = new Establishment("agency", "Agence Guéliz", "Administration", "Marrakech", "AG",
                "#df8d62", "#fff0e6", "Avenue Mohammed V, Marrakech", "08:30", "16:30");
        clinicQueue = new ServiceQueue(clinic, "Dépôt de dossier", "A", 3, "Guichet 3");
        ReflectionTestUtils.setField(clinicQueue, "id", 1L);
        agencyQueue = new ServiceQueue(agency, "Retrait de documents", "B", 2, "Guichet 1");
        ReflectionTestUtils.setField(agencyQueue, "id", 2L);
    }

    @Test
    void clinicStaffCanUseTheClinicCounter() {
        when(queues.findById(clinicQueue.getId())).thenReturn(Optional.of(clinicQueue));

        assertEquals(true, service.counter(clinicAgent, clinicQueue.getId()).queue().open());
    }

    @Test
    void clinicStaffCannotUseTheAgencyCounter() {
        when(queues.findById(agencyQueue.getId())).thenReturn(Optional.of(agencyQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.counter(clinicAgent, agencyQueue.getId()));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void agencyStaffCannotUseTheClinicCounter() {
        when(queues.findById(clinicQueue.getId())).thenReturn(Optional.of(clinicQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.counter(agencyAgent, clinicQueue.getId()));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void aStaffTokenWithoutASiteClaimIsRefused() {
        when(queues.findById(clinicQueue.getId())).thenReturn(Optional.of(clinicQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.counter(staffWithoutSite, clinicQueue.getId()));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void anAdminIsNotABypass() {
        when(queues.findById(agencyQueue.getId())).thenReturn(Optional.of(agencyQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.counter(clinicAdmin, agencyQueue.getId()));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void clinicStaffCannotCallNextAtTheAgency() {
        when(queues.findByIdForUpdate(agencyQueue.getId())).thenReturn(Optional.of(agencyQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.callNext(clinicAgent, agencyQueue.getId()));

        assertEquals(403, exception.getStatusCode().value());
        verify(tickets, never()).findFirstByQueueIdAndStatusOrderByNumberAsc(any(), any());
    }

    @Test
    void agencyStaffCannotServeAClinicTicket() {
        Ticket called = new Ticket(clinicQueue, 1, "client-1", "Client");
        called.call();
        when(tickets.findById(called.getId())).thenReturn(Optional.of(called));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.serve(agencyAgent, called.getId()));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(TicketStatus.CALLED, called.getStatus());
    }

    @Test
    void clinicStaffCanServeAClinicTicket() {
        Ticket called = new Ticket(clinicQueue, 1, "client-1", "Client");
        called.call();
        when(tickets.findById(called.getId())).thenReturn(Optional.of(called));

        service.serve(clinicAgent, called.getId());

        assertEquals(TicketStatus.SERVED, called.getStatus());
    }

    @Test
    void agencyStaffCannotCloseAClinicQueue() {
        when(queues.findByIdForUpdate(clinicQueue.getId())).thenReturn(Optional.of(clinicQueue));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.setOpen(agencyAgent, clinicQueue.getId(), false));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals(true, clinicQueue.isOpen());
    }

    @Test
    void clientsStillTakeTicketsInAnyEstablishment() {
        when(queues.findByIdForUpdate(agencyQueue.getId())).thenReturn(Optional.of(agencyQueue));
        when(tickets.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var ticket = service.takeTicket(client, agencyQueue.getId());

        assertEquals("agency", ticket.establishmentId());
        assertEquals(TicketStatus.WAITING, ticket.status());
    }
}
