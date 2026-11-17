package ma.ensa.queueservice.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repairs rows created before Slice 1 added tenant columns. Runs after DemoData and is
 * idempotent: every assignment is guarded by a null check.
 */
@Component
@Order(20)
public class TenantBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantBackfill.class);

    private final EstablishmentRepository establishments;
    private final ServiceQueueRepository queues;
    private final TicketRepository tickets;

    public TenantBackfill(EstablishmentRepository establishments,
                          ServiceQueueRepository queues,
                          TicketRepository tickets) {
        this.establishments = establishments;
        this.queues = queues;
        this.tickets = tickets;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int establishmentsUpdated = 0;
        for (Establishment establishment : establishments.findAll()) {
            if (establishment.getOrganizationId() == null) {
                establishment.assignOrganizationId(establishment.getId());
                establishmentsUpdated++;
            }
        }

        int queuesUpdated = 0;
        for (ServiceQueue queue : queues.findAll()) {
            if (queue.ensureOpen()) {
                queuesUpdated++;
            }
        }

        int ticketsUpdated = 0;
        for (Ticket ticket : tickets.findAll()) {
            if (ticket.getOrganizationId() == null) {
                Establishment establishment = ticket.getQueue().getEstablishment();
                String organizationId = establishment.getOrganizationId() == null
                        ? establishment.getId()
                        : establishment.getOrganizationId();
                ticket.assignOrganizationId(organizationId);
                ticketsUpdated++;
            }
        }

        log.info("tenant backfill: establishments={}, queues={}, tickets={}",
                establishmentsUpdated, queuesUpdated, ticketsUpdated);
    }
}
