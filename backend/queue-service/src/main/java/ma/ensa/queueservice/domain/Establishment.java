package ma.ensa.queueservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A place people queue at: a clinic, an administration office...
 * The id is a readable slug ("clinic", "agency") because the frontend routes on it.
 */
@Entity
@Table(indexes = @Index(columnList = "organization_id"))
public class Establishment {

    @Id
    private String id;

    @Column(name = "organization_id", length = 64)
    private String organizationId;

    private String name;
    private String category;
    private String city;
    private String shortName;
    private String color;
    private String tint;
    private String address;
    private String opensAt;
    private String closesAt;

    /**
     * A suspended establishment keeps its data and lets in-flight tickets finish, but refuses
     * new tickets.
     */
    @Column(nullable = false)
    private boolean active = true;

    protected Establishment() {
    }

    public Establishment(String id, String name, String category, String city, String shortName,
                         String color, String tint, String address, String opensAt, String closesAt) {
        this(id, name, category, city, shortName, color, tint, address, opensAt, closesAt, id);
    }

    public Establishment(String id, String name, String category, String city, String shortName,
                         String color, String tint, String address, String opensAt, String closesAt,
                         String organizationId) {
        this.id = id;
        this.organizationId = organizationId == null ? id : organizationId;
        this.name = name;
        this.category = category;
        this.city = city;
        this.shortName = shortName;
        this.color = color;
        this.tint = tint;
        this.address = address;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    public String getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getCity() { return city; }
    public String getShortName() { return shortName; }
    public String getColor() { return color; }
    public String getTint() { return tint; }
    public String getAddress() { return address; }
    public String getOpensAt() { return opensAt; }
    public String getClosesAt() { return closesAt; }
    public boolean isActive() { return active; }

    void suspend() { this.active = false; }

    void reactivate() { this.active = true; }

    void assignOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}
