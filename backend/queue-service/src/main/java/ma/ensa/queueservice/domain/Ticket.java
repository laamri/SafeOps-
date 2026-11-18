package ma.ensa.queueservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(indexes = {
        @Index(columnList = "ownerSubject"),
        @Index(columnList = "queue_id, status"),
        @Index(columnList = "organization_id, queue_id, status")
})
public class Ticket {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ServiceQueue queue;

    @Column(name = "organization_id", length = 64)
    private String organizationId;

    private int number;
    private String code;
    // Keycloak "sub" of the client, as forwarded by the gateway
    private String ownerSubject;
    private String ownerName;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private Instant createdAt;
    private Instant calledAt;
    private Instant closedAt;

    protected Ticket() {
    }

    public Ticket(ServiceQueue queue, int number, String ownerSubject, String ownerName) {
        this.id = UUID.randomUUID();
        this.queue = queue;
        this.organizationId = queue.getEstablishment().getOrganizationId() == null
                ? queue.getEstablishment().getId()
                : queue.getEstablishment().getOrganizationId();
        this.number = number;
        this.code = "%s-%03d".formatted(queue.getPrefix(), number);
        this.ownerSubject = ownerSubject;
        this.ownerName = ownerName;
        this.status = TicketStatus.WAITING;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == TicketStatus.WAITING || status == TicketStatus.CALLED;
    }

    public void call() {
        moveTo(TicketStatus.WAITING, TicketStatus.CALLED);
        calledAt = Instant.now();
    }

    public void cancel() {
        moveTo(TicketStatus.WAITING, TicketStatus.CANCELLED);
        closedAt = Instant.now();
    }

    public void serve() {
        moveTo(TicketStatus.CALLED, TicketStatus.SERVED);
        closedAt = Instant.now();
    }

    public void noShow() {
        moveTo(TicketStatus.CALLED, TicketStatus.NO_SHOW);
        closedAt = Instant.now();
    }

    private void moveTo(TicketStatus required, TicketStatus next) {
        if (status != required) {
            throw new TicketTransitionException(code, status, next);
        }
        status = next;
    }

    public UUID getId() { return id; }
    public ServiceQueue getQueue() { return queue; }
    public String getOrganizationId() { return organizationId; }
    public int getNumber() { return number; }
    public String getCode() { return code; }
    public String getOwnerSubject() { return ownerSubject; }
    public String getOwnerName() { return ownerName; }
    public TicketStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCalledAt() { return calledAt; }

    void assignOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
