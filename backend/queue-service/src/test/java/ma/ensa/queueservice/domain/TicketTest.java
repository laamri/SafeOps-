package ma.ensa.queueservice.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketTest {

    private final ServiceQueue queue = new ServiceQueue(establishment(), "Dépôt de dossier", "A", 3, "Guichet 3");

    @Test
    void waitingTicketCanBeCancelled() {
        Ticket ticket = ticket();

        ticket.cancel();

        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
    }

    @Test
    void waitingTicketCannotBeServed() {
        Ticket ticket = ticket();

        TicketTransitionException exception = assertThrows(TicketTransitionException.class, ticket::serve);

        assertEquals(TicketStatus.WAITING, exception.from());
        assertEquals(TicketStatus.SERVED, exception.to());
        assertEquals(TicketStatus.WAITING, ticket.getStatus());
    }

    @Test
    void waitingTicketCannotBeMarkedNoShow() {
        Ticket ticket = ticket();

        assertThrows(TicketTransitionException.class, ticket::noShow);

        assertEquals(TicketStatus.WAITING, ticket.getStatus());
    }

    @Test
    void calledTicketCanBeServed() {
        Ticket ticket = ticket();
        ticket.call();

        ticket.serve();

        assertEquals(TicketStatus.SERVED, ticket.getStatus());
    }

    @Test
    void calledTicketCanBeMarkedNoShow() {
        Ticket ticket = ticket();
        ticket.call();

        ticket.noShow();

        assertEquals(TicketStatus.NO_SHOW, ticket.getStatus());
    }

    @Test
    void calledTicketCannotBeCancelledByItsOwner() {
        Ticket ticket = ticket();
        ticket.call();

        assertThrows(TicketTransitionException.class, ticket::cancel);

        assertEquals(TicketStatus.CALLED, ticket.getStatus());
    }

    @Test
    void aTicketCanOnlyBeCalledOnce() {
        Ticket ticket = ticket();
        ticket.call();

        assertThrows(TicketTransitionException.class, ticket::call);
    }

    @Test
    void terminalTicketsCannotChangeAnyMore() {
        Ticket ticket = ticket();
        ticket.call();
        ticket.serve();

        assertThrows(TicketTransitionException.class, ticket::serve);
        assertThrows(TicketTransitionException.class, ticket::noShow);
        assertThrows(TicketTransitionException.class, ticket::cancel);
        assertThrows(TicketTransitionException.class, ticket::call);
        assertEquals(TicketStatus.SERVED, ticket.getStatus());
    }

    private Ticket ticket() {
        return new Ticket(queue, 1, "client-1", "Client");
    }

    private Establishment establishment() {
        return new Establishment("clinic", "Clinique Al Amal", "Clinique", "Casablanca", "CA",
                "#41a98a", "#e0f4ec", "Rue Abou Alaa Zahar, Casablanca", "08:30", "16:30");
    }
}
