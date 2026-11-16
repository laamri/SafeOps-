package ma.ensa.tenantservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public record CurrentUser(String subject, String username, String tenantId, Set<String> roles) {
    public static final String TENANT_ADMIN = "tenant-admin";
    public static final String PROVIDER_ADMIN = "provider-admin";

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public void requireProviderAdmin() {
        if (!hasRole(PROVIDER_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "provider-admin role required");
        }
    }

    public void requireTenantAdmin() {
        if (!hasRole(TENANT_ADMIN) || tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant-admin role and tenant claim required");
        }
    }
}
