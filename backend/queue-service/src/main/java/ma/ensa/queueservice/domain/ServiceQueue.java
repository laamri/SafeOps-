package ma.ensa.queueservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * One line inside an establishment ("Dépôt de dossier", "Retrait de documents").
 * Ticket numbers are handed out from {@link #lastNumber}, so taking a ticket must lock the row.
 */
@Entity
public class ServiceQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Establishment establishment;

    private String name;
    // letter in front of the ticket number: A-042
    private String prefix;
    private int avgServiceMinutes;
    // the counter that serves this queue, shown to the client when called
    private String counter;
    private int lastNumber;

    // null on rows created before Slice 1; isOpen() treats null as open until backfill runs.
    @Column(name = "is_open")
    private Boolean open;

    protected ServiceQueue() {
    }

    public ServiceQueue(Establishment establishment, String name, String prefix, int avgServiceMinutes, String counter) {
        this.establishment = establishment;
        this.name = name;
        this.prefix = prefix;
        this.avgServiceMinutes = avgServiceMinutes;
        this.counter = counter;
        this.open = true;
    }

    public int nextNumber() {
        return ++lastNumber;
    }

    public boolean isOpen() {
        return open == null || open;
    }

    public boolean ensureOpen() {
        if (open == null) {
            open = true;
            return true;
        }
        return false;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    void update(String name, String prefix, int avgServiceMinutes, String counter) {
        this.name = name;
        this.prefix = prefix;
        this.avgServiceMinutes = avgServiceMinutes;
        this.counter = counter;
    }

    public Long getId() { return id; }
    public Establishment getEstablishment() { return establishment; }
    public String getName() { return name; }
    public String getPrefix() { return prefix; }
    public int getAvgServiceMinutes() { return avgServiceMinutes; }
    public String getCounter() { return counter; }
}
