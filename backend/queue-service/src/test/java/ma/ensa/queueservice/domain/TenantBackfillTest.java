package ma.ensa.queueservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantBackfillTest {

    private final EstablishmentRepository establishments = mock(EstablishmentRepository.class);
    private final ServiceQueueRepository queues = mock(ServiceQueueRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TenantBackfill backfill = new TenantBackfill(establishments, queues, tickets);

    @Test
    void fillsMissingTenantColumnsAndKeepsExistingValues() {
        Establishment legacy = establishment("clinic");
        legacy.assignOrganizationId(null);
        Establishment tagged = establishment("agency");

        ServiceQueue closedQueue = new ServiceQueue(tagged, "Retrait de documents", "B", 2, "Guichet 1");
        closedQueue.setOpen(false);
        ServiceQueue legacyQueue = new ServiceQueue(legacy, "Dépôt de dossier", "A", 3, "Guichet 3");
        ReflectionTestUtils.setField(legacyQueue, "open", null);

        Ticket legacyTicket = new Ticket(legacyQueue, 1, "client-1", "Client");
        legacyTicket.assignOrganizationId(null);
        Ticket taggedTicket = new Ticket(closedQueue, 2, "client-2", "Client");

        when(establishments.findAll()).thenReturn(List.of(legacy, tagged));
        when(queues.findAll()).thenReturn(List.of(legacyQueue, closedQueue));
        when(tickets.findAll()).thenReturn(List.of(legacyTicket, taggedTicket));

        backfill.run(null);

        assertEquals("clinic", legacy.getOrganizationId());
        assertEquals("agency", tagged.getOrganizationId());
        assertTrue(legacyQueue.isOpen());
        assertFalse(closedQueue.isOpen());
        assertEquals("clinic", legacyTicket.getOrganizationId());
        assertEquals("agency", taggedTicket.getOrganizationId());
    }

    @Test
    void isIdempotentAcrossRuns() {
        Establishment establishment = establishment("clinic");
        establishment.assignOrganizationId(null);

        ServiceQueue queue = new ServiceQueue(establishment, "Dépôt de dossier", "A", 3, "Guichet 3");
        ReflectionTestUtils.setField(queue, "open", null);

        Ticket ticket = new Ticket(queue, 1, "client-1", "Client");
        ticket.assignOrganizationId(null);

        when(establishments.findAll()).thenReturn(List.of(establishment));
        when(queues.findAll()).thenReturn(List.of(queue));
        when(tickets.findAll()).thenReturn(List.of(ticket));

        backfill.run(null);
        backfill.run(null);

        assertEquals("clinic", establishment.getOrganizationId());
        assertTrue(queue.isOpen());
        assertEquals("clinic", ticket.getOrganizationId());
    }

    @Test
    void legacyQueueReadsAsOpenBeforeBackfill() {
        ServiceQueue queue = new ServiceQueue(establishment("clinic"), "Dépôt de dossier", "A", 3, "Guichet 3");
        ReflectionTestUtils.setField(queue, "open", null);

        assertTrue(queue.isOpen());
    }

    private Establishment establishment(String id) {
        return new Establishment(id, "Name", "Category", "City", "SN", "#ffffff", "#000000",
                "Address", "08:30", "16:30");
    }
}
