package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class QueueServiceTest {

    private EstablishmentRepository establishments;
    private ServiceQueueRepository queues;
    private TicketRepository tickets;
    private QueueService service;
    private ServiceQueue queue;

    private final CurrentUser agent = new CurrentUser("agent-1", "agent", Set.of("agent"));

    @BeforeEach
    void setUp() {
        establishments = mock(EstablishmentRepository.class);
        queues = mock(ServiceQueueRepository.class);
        tickets = mock(TicketRepository.class);
        service = new QueueService(establishments, queues, tickets);
        Establishment clinic = new Establishment("clinic", "Clinique Al Amal", "Clinique", "Casablanca", "CA",
                "#41a98a", "#e0f4ec", "Rue Abou Alaa Zahar, Casablanca", "08:30", "16:30");
        queue = new ServiceQueue(clinic, "Dépôt de dossier", "A", 3, "Guichet 3");
    }

    @Test
    void servingAWaitingTicketIsRejected() {
        Ticket waiting = new Ticket(queue, 1, "client-1", "Client");
        when(tickets.findById(waiting.getId())).thenReturn(Optional.of(waiting));

        TicketTransitionException exception = assertThrows(TicketTransitionException.class,
                () -> service.serve(agent, waiting.getId()));

        assertEquals(TicketStatus.SERVED, exception.to());
        assertEquals(TicketStatus.WAITING, waiting.getStatus());
    }

    @Test
    void completingOnlyWorksOnCalledTickets() {
        Ticket called = new Ticket(queue, 1, "client-1", "Client");
        called.call();
        when(tickets.findById(called.getId())).thenReturn(Optional.of(called));
        when(tickets.findByQueueIdAndStatusOrderByNumberAsc(any(), any())).thenReturn(List.of());
        when(tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.CALLED))
                .thenReturn(List.of(called));

        service.serve(agent, called.getId());

        assertEquals(TicketStatus.SERVED, called.getStatus());
    }

    @Test
    void cancellingACalledTicketIsRejected() {
        Ticket called = new Ticket(queue, 1, "client-1", "Client");
        called.call();
        when(tickets.findById(called.getId())).thenReturn(Optional.of(called));

        assertThrows(TicketTransitionException.class,
                () -> service.cancel(new CurrentUser("client-1", "client", Set.of()), called.getId()));

        assertEquals(TicketStatus.CALLED, called.getStatus());
    }

    @Test
    void callNextServesTheCurrentTicketAndCallsTheFirstWaitingOne() {
        Ticket current = new Ticket(queue, 1, "client-1", "Client");
        current.call();
        Ticket next = new Ticket(queue, 2, "client-2", "Client");
        when(queues.findByIdForUpdate(queue.getId())).thenReturn(Optional.of(queue));
        when(tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.CALLED))
                .thenReturn(List.of(current));
        when(tickets.findFirstByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.WAITING))
                .thenReturn(Optional.of(next));

        service.callNext(agent, queue.getId());

        assertEquals(TicketStatus.SERVED, current.getStatus());
        assertEquals(TicketStatus.CALLED, next.getStatus());
    }

    @Test
    void aClientCannotUseStaffOperations() {
        Ticket called = new Ticket(queue, 1, "client-1", "Client");
        called.call();
        when(tickets.findById(called.getId())).thenReturn(Optional.of(called));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.serve(new CurrentUser("client-1", "client", Set.of()), called.getId()));

        assertEquals(403, exception.getStatusCode().value());
        verify(tickets, never()).save(any(Ticket.class));
    }
}
