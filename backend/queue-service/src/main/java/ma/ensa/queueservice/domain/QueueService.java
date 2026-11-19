package ma.ensa.queueservice.domain;

import ma.ensa.queueservice.security.CurrentUser;
import ma.ensa.queueservice.web.Views.CounterView;
import ma.ensa.queueservice.web.Views.EstablishmentCreateRequest;
import ma.ensa.queueservice.web.Views.EstablishmentUpdateRequest;
import ma.ensa.queueservice.web.Views.EstablishmentView;
import ma.ensa.queueservice.web.Views.ScheduleRequest;
import ma.ensa.queueservice.web.Views.WeeklyHoursRequest;
import ma.ensa.queueservice.web.Views.WeeklyHoursView;
import ma.ensa.queueservice.web.Views.QueueRequest;
import ma.ensa.queueservice.web.Views.QueueView;
import ma.ensa.queueservice.web.Views.TicketView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class QueueService {

    private static final Set<TicketStatus> ACTIVE = Set.of(TicketStatus.WAITING, TicketStatus.CALLED);

    private final EstablishmentRepository establishments;
    private final ServiceQueueRepository queues;
    private final TicketRepository tickets;
    private final WeeklyHoursRepository hours;

    public QueueService(EstablishmentRepository establishments, ServiceQueueRepository queues,
                        TicketRepository tickets, WeeklyHoursRepository hours) {
        this.establishments = establishments;
        this.queues = queues;
        this.tickets = tickets;
        this.hours = hours;
    }

    // ---------------------------------------------------------------- establishments

    @Transactional(readOnly = true)
    public List<EstablishmentView> establishments(String query, String category) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return establishments.findAll().stream()
                .filter(e -> category == null || category.isBlank() || category.equalsIgnoreCase(e.getCategory()))
                .filter(e -> q.isEmpty() || (e.getName() + " " + e.getCity()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Establishment::getName))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstablishmentView establishment(String id) {
        return establishments.findById(id)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown establishment " + id));
    }

    // ---------------------------------------------------------------- administration

    public EstablishmentView createEstablishment(CurrentUser user, EstablishmentCreateRequest request) {
        user.requireAdmin();
        // the row only backs a site the caller already holds in the token; it never widens it
        user.requireSite(request.id());
        if (establishments.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "establishment " + request.id() + " already exists");
        }
        Establishment created = new Establishment(request.id(), request.name(), request.category(), request.city(),
                request.shortName(), request.color(), request.tint(), request.address(), request.opensAt(),
                request.closesAt(), request.id());
        return toView(establishments.save(created));
    }

    public EstablishmentView updateEstablishment(CurrentUser user, String id, EstablishmentUpdateRequest request) {
        user.requireAdmin();
        Establishment establishment = establishmentEntity(id);
        user.requireSite(establishment.getOrganizationId());
        establishment.updateProfile(request.name(), request.category(), request.city(), request.shortName(),
                request.color(), request.tint(), request.address(), request.opensAt(), request.closesAt());
        return toView(establishment);
    }

    /**
     * Suspension keeps the data and lets in-flight tickets finish; it only refuses new ones.
     */
    public EstablishmentView setEstablishmentActive(CurrentUser user, String id, boolean active) {
        user.requireAdmin();
        Establishment establishment = establishmentEntity(id);
        user.requireSite(establishment.getOrganizationId());
        if (active) {
            establishment.reactivate();
        } else {
            establishment.suspend();
        }
        return toView(establishment);
    }

    @Transactional(readOnly = true)
    public List<WeeklyHoursView> schedule(CurrentUser user, String id) {
        user.requireAdmin();
        Establishment establishment = establishmentEntity(id);
        user.requireSite(establishment.getOrganizationId());
        return hours.findByEstablishmentIdOrderByDayOfWeekAscOpensAtAsc(id).stream()
                .map(QueueService::toView)
                .toList();
    }

    /**
     * Replaces the whole calendar. Intervals are ordered before checking, so an overlap is the
     * only way two intervals of one day can collide; the database re-checks the range itself.
     */
    public List<WeeklyHoursView> replaceSchedule(CurrentUser user, String id, ScheduleRequest request) {
        user.requireAdmin();
        Establishment establishment = establishmentEntity(id);
        user.requireSite(establishment.getOrganizationId());

        var intervals = request.intervals().stream()
                .sorted(Comparator.comparing(WeeklyHoursRequest::dayOfWeek).thenComparing(WeeklyHoursRequest::opensAt))
                .toList();
        int previousDay = -1;
        LocalTime previousClose = null;
        for (WeeklyHoursRequest interval : intervals) {
            LocalTime opensAt = LocalTime.parse(interval.opensAt());
            LocalTime closesAt = LocalTime.parse(interval.closesAt());
            if (!opensAt.isBefore(closesAt)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "opensAt must be before closesAt");
            }
            if (interval.dayOfWeek() == previousDay && opensAt.isBefore(previousClose)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "opening intervals overlap");
            }
            previousDay = interval.dayOfWeek();
            previousClose = closesAt;
        }

        hours.deleteByEstablishmentId(id);
        List<WeeklyHours> saved = hours.saveAll(intervals.stream()
                .map(interval -> new WeeklyHours(establishment, interval.dayOfWeek().shortValue(),
                        LocalTime.parse(interval.opensAt()), LocalTime.parse(interval.closesAt())))
                .toList());
        return saved.stream().map(QueueService::toView).toList();
    }

    private static WeeklyHoursView toView(WeeklyHours interval) {
        return new WeeklyHoursView(interval.getDayOfWeek(), interval.getOpensAt().toString(),
                interval.getClosesAt().toString());
    }

    public QueueView createQueue(CurrentUser user, String establishmentId, QueueRequest request) {        user.requireAdmin();
        Establishment establishment = establishmentEntity(establishmentId);
        user.requireSite(establishment.getOrganizationId());
        ServiceQueue queue = new ServiceQueue(establishment, request.name(), request.prefix(),
                request.avgServiceMinutes(), request.counter());
        return toView(queues.save(queue));
    }

    public QueueView updateQueue(CurrentUser user, Long queueId, QueueRequest request) {
        user.requireAdmin();
        ServiceQueue queue = lockedQueue(queueId);
        user.requireSite(queue.getEstablishment().getOrganizationId());
        queue.update(request.name(), request.prefix(), request.avgServiceMinutes(), request.counter());
        return toView(queue);
    }

    /**
     * A queue that already handed out tickets is history; it can be closed, not deleted.
     * The row lock keeps a ticket from being created between the check and the delete.
     */
    public void deleteQueue(CurrentUser user, Long queueId) {
        user.requireAdmin();
        ServiceQueue queue = lockedQueue(queueId);
        user.requireSite(queue.getEstablishment().getOrganizationId());
        if (tickets.existsByQueueId(queueId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this queue has tickets; it cannot be deleted");
        }
        queues.delete(queue);
    }

    // ---------------------------------------------------------------- client side

    public TicketView takeTicket(CurrentUser user, Long queueId) {
        ServiceQueue queue = queues.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + queueId));
        if (!queue.getEstablishment().isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this establishment is suspended");
        }
        if (!queue.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this queue is closed");
        }
        if (tickets.existsByQueueIdAndOwnerSubjectAndStatusIn(queueId, user.subject(), ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "you already hold a ticket in this queue");
        }
        Ticket ticket = tickets.save(new Ticket(queue, queue.nextNumber(), user.subject(), user.username()));
        return toView(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketView> myActiveTickets(CurrentUser user) {
        return tickets.findByOwnerSubjectAndStatusInOrderByCreatedAtDesc(user.subject(), ACTIVE).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketView myTicket(CurrentUser user, UUID id) {
        return toView(owned(user, id));
    }

    public TicketView cancel(CurrentUser user, UUID id) {
        Ticket ticket = owned(user, id);
        ticket.cancel();
        return toView(ticket);
    }

    // ---------------------------------------------------------------- agent side

    @Transactional(readOnly = true)
    public CounterView counter(CurrentUser user, Long queueId) {
        user.requireStaff();
        ServiceQueue queue = queue(queueId);
        requireSite(user, queue);
        return counterView(queue);
    }

    /**
     * Closes whoever is at the counter as served, then calls the next waiting ticket.
     */
    public CounterView callNext(CurrentUser user, Long queueId) {
        user.requireStaff();
        ServiceQueue queue = queues.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + queueId));
        requireSite(user, queue);
        if (!queue.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this queue is closed");
        }
        tickets.findByQueueIdAndStatusOrderByNumberAsc(queueId, TicketStatus.CALLED)
                .forEach(Ticket::serve);
        tickets.findFirstByQueueIdAndStatusOrderByNumberAsc(queueId, TicketStatus.WAITING)
                .ifPresent(Ticket::call);
        return counterView(queue);
    }

    public CounterView setOpen(CurrentUser user, Long queueId, boolean open) {
        user.requireStaff();
        ServiceQueue queue = queues.findByIdForUpdate(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + queueId));
        requireSite(user, queue);
        queue.setOpen(open);
        return counterView(queue);
    }

    public CounterView serve(CurrentUser user, UUID ticketId) {
        user.requireStaff();
        Ticket ticket = ticket(ticketId);
        requireSite(user, ticket.getQueue());
        ticket.serve();
        return counterView(ticket.getQueue());
    }

    public CounterView noShow(CurrentUser user, UUID ticketId) {
        user.requireStaff();
        Ticket ticket = ticket(ticketId);
        requireSite(user, ticket.getQueue());
        ticket.noShow();
        return counterView(ticket.getQueue());
    }

    // ---------------------------------------------------------------- mapping

    private void requireSite(CurrentUser user, ServiceQueue queue) {
        user.requireSite(queue.getEstablishment().getOrganizationId());
    }

    private Ticket ticket(UUID id) {
        return tickets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown ticket"));
    }

    private Ticket owned(CurrentUser user, UUID id) {
        return tickets.findById(id)
                .filter(t -> t.getOwnerSubject().equals(user.subject()))
                // same answer for "not yours" and "does not exist": ids are not to be probed
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown ticket"));
    }

    private ServiceQueue queue(Long id) {
        return queues.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + id));
    }

    private Establishment establishmentEntity(String id) {
        return establishments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown establishment " + id));
    }

    private ServiceQueue lockedQueue(Long id) {
        return queues.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown queue " + id));
    }

    private CounterView counterView(ServiceQueue queue) {
        TicketView current = tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.CALLED).stream()
                .reduce((first, second) -> second)
                .map(this::toView)
                .orElse(null);
        List<TicketView> waiting = tickets.findByQueueIdAndStatusOrderByNumberAsc(queue.getId(), TicketStatus.WAITING).stream()
                .map(this::toView)
                .toList();
        return new CounterView(toView(queue), current, waiting);
    }

    private EstablishmentView toView(Establishment e) {
        List<QueueView> queueViews = queues.findByEstablishmentIdOrderByIdAsc(e.getId()).stream()
                .map(this::toView)
                .toList();
        long waiting = queueViews.stream().mapToLong(QueueView::waiting).sum();
        long estimate = queueViews.stream().mapToLong(QueueView::estimatedWaitMinutes).max().orElse(0);
        return new EstablishmentView(e.getId(), e.getName(), e.getCategory(), e.getCity(), e.getShortName(),
                e.getColor(), e.getTint(), e.getAddress(), e.getOpensAt(), e.getClosesAt(), e.isActive(),
                waiting, estimate, queueViews);
    }

    private QueueView toView(ServiceQueue q) {
        long waiting = tickets.countByQueueIdAndStatus(q.getId(), TicketStatus.WAITING);
        return new QueueView(q.getId(), q.getName(), q.getPrefix(), q.getCounter(), q.getAvgServiceMinutes(),
                waiting, waiting * q.getAvgServiceMinutes(), q.isOpen());
    }

    private TicketView toView(Ticket t) {
        ServiceQueue q = t.getQueue();
        Establishment e = q.getEstablishment();
        long position = 0;
        if (t.getStatus() == TicketStatus.WAITING) {
            position = tickets.countByQueueIdAndStatusAndNumberLessThan(q.getId(), TicketStatus.WAITING, t.getNumber()) + 1;
        }
        // the person at position 1 still waits for whoever is at the counter
        long estimate = position * q.getAvgServiceMinutes();
        return new TicketView(t.getId(), t.getCode(), t.getStatus(), e.getId(), e.getName(), e.getShortName(),
                e.getColor(), e.getTint(), q.getId(), q.getName(), q.getCounter(), t.getOwnerName(),
                position, estimate, t.getCreatedAt(), t.getCalledAt());
    }
}
