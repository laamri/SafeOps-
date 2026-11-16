package ma.ensa.tenantservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 80)
    private String slug;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 40)
    private String plan;
    @Column(nullable = false, length = 40)
    private String status;
    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tenant() {
    }

    public Tenant(UUID id, String slug, String name, String plan, String status, String configJson) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.plan = plan;
        this.status = status;
        this.configJson = configJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getPlan() { return plan; }
    public String getStatus() { return status; }
    public String getConfigJson() { return configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateConfig(String name, String configJson) {
        this.name = name;
        this.configJson = configJson;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
