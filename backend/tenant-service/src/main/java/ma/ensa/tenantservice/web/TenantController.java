package ma.ensa.tenantservice.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ma.ensa.tenantservice.domain.Tenant;
import ma.ensa.tenantservice.domain.TenantRepository;
import ma.ensa.tenantservice.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class TenantController {
    private final TenantRepository repository;
    private final TenantConfigValidator validator;

    public TenantController(TenantRepository repository, TenantConfigValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @GetMapping({"/api/tenant/config", "/api/tenant/config/{slug}"})
    public ResponseEntity<TenantConfigResponse> config(CurrentUser user,
                                                       @PathVariable(required = false) String slug) {
        if (slug != null && !slug.equals(user.tenantId())) {
            return ResponseEntity.notFound().build();
        }
        return tenantFor(user).map(tenant -> ResponseEntity.ok(toConfig(tenant)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping({"/api/tenant/config", "/api/tenant/config/{slug}"})
    public ResponseEntity<TenantConfigResponse> updateConfig(CurrentUser user,
                                                             @PathVariable(required = false) String slug,
                                                             @RequestBody String configJson) {
        user.requireTenantAdmin();
        if (slug != null && !slug.equals(user.tenantId())) {
            return ResponseEntity.notFound().build();
        }
        Tenant tenant = tenantFor(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found"));
        validator.validate(configJson);
        JsonObject branding = JsonParser.parseString(configJson).getAsJsonObject().getAsJsonObject("branding");
        tenant.updateConfig(branding.get("name").getAsString(), configJson);
        return ResponseEntity.ok(toConfig(repository.save(tenant)));
    }

    @GetMapping("/api/public/tenants/{slug}/branding")
    public ResponseEntity<BrandingResponse> branding(@PathVariable String slug) {
        return repository.findBySlug(slug).map(tenant -> ResponseEntity.ok(toBranding(tenant)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private java.util.Optional<Tenant> tenantFor(CurrentUser user) {
        if (user.tenantId() == null || user.tenantId().isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findBySlug(user.tenantId());
    }

    private TenantConfigResponse toConfig(Tenant tenant) {
        return new TenantConfigResponse(tenant.getSlug(), tenant.getName(), tenant.getPlan(),
                tenant.getStatus(), tenant.getConfigJson());
    }

    private BrandingResponse toBranding(Tenant tenant) {
        JsonObject branding = JsonParser.parseString(tenant.getConfigJson()).getAsJsonObject()
                .getAsJsonObject("branding");
        return new BrandingResponse(tenant.getSlug(), branding.get("name").getAsString(),
                branding.has("logoUrl") && !branding.get("logoUrl").isJsonNull() ? branding.get("logoUrl").getAsString() : null,
                branding.get("primaryColor").getAsString(), branding.get("accentColor").getAsString());
    }

    public record TenantConfigResponse(String slug, String name, String plan, String status, String config) {}
    public record BrandingResponse(String slug, String name, String logoUrl, String primaryColor, String accentColor) {}
}
