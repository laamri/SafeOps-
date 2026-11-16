package ma.ensa.tenantservice.web;

import ma.ensa.tenantservice.domain.Tenant;
import ma.ensa.tenantservice.domain.TenantRepository;
import ma.ensa.tenantservice.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class ProviderController {
    private final TenantRepository repository;
    private final TenantConfigValidator validator;

    public ProviderController(TenantRepository repository, TenantConfigValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @PostMapping("/api/provider/tenants")
    public TenantSummary create(CurrentUser user, @RequestBody CreateTenantRequest request) {
        user.requireProviderAdmin();
        if (repository.findBySlug(request.slug()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug already exists");
        }
        String config = request.config() == null ? defaultConfig(request.name()) : request.config();
        validator.validate(config);
        return summary(repository.save(new Tenant(UUID.randomUUID(), request.slug(), request.name(),
                request.plan() == null ? "TRIAL" : request.plan(), "ACTIVE", config)));
    }

    @GetMapping("/api/provider/tenants")
    public List<TenantSummary> list(CurrentUser user) {
        user.requireProviderAdmin();
        return repository.findAll().stream().map(this::summary).toList();
    }

    @PatchMapping("/api/provider/tenants/{slug}/status")
    public TenantSummary updateStatus(CurrentUser user, @PathVariable String slug, @RequestBody StatusRequest request) {
        user.requireProviderAdmin();
        Tenant tenant = repository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        if (!request.status().matches("ACTIVE|SUSPENDED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or SUSPENDED");
        }
        tenant.updateStatus(request.status());
        return summary(repository.save(tenant));
    }

    private TenantSummary summary(Tenant tenant) {
        return new TenantSummary(tenant.getSlug(), tenant.getName(), tenant.getPlan(), tenant.getStatus(),
                tenant.getCreatedAt(), tenant.getUpdatedAt());
    }

    private String defaultConfig(String name) {
        return "{\"branding\":{\"name\":\"" + name.replace("\"", "") +
                "\",\"primaryColor\":\"#1FA38A\",\"accentColor\":\"#F3B761\"}}";
    }

    public record CreateTenantRequest(String slug, String name, String plan, String config) {}
    public record StatusRequest(String status) {}
    public record TenantSummary(String slug, String name, String plan, String status,
                                java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
