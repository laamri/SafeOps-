package ma.ensa.queueservice.domain;

/**
 * A ticket was asked to move to a status its current status does not allow.
 * Mapped to 409 by the web layer.
 */
public class TicketTransitionException extends RuntimeException {

    private final TicketStatus from;
    private final TicketStatus to;

    public TicketTransitionException(String ticketCode, TicketStatus from, TicketStatus to) {
        super("ticket " + ticketCode + " cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public TicketStatus from() {
        return from;
    }

    public TicketStatus to() {
        return to;
    }
}
