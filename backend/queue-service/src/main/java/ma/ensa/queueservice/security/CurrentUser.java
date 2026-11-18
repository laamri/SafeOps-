package ma.ensa.queueservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * The caller, as the api-gateway vouched for it in the X-Auth-* headers.
 * <p>
 * This service never sees the token: it trusts those headers because the gateway verifies the
 * token, strips any X-Auth-* the client sent, and is the only way in (this service publishes no
 * port in docker-compose). Exposing it directly would let anyone claim any identity.
 * <p>
 * {@code siteIds} comes from the caller's Keycloak tenant groups. In Slice 1 an establishment's
 * organisation id is also its site id. Staff operations require the site: a staff token without
 * one is refused, never widened to "all sites".
 */
public record CurrentUser(String subject, String username, Set<String> roles, Set<String> siteIds) {

    public static final String AGENT = "agent";
    public static final String ADMIN = "admin";

    public boolean hasAnyRole(String... wanted) {
        for (String role : wanted) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public void requireStaff() {
        if (!hasAnyRole(AGENT, ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "agent or admin role required");
        }
    }

    public void requireAdmin() {
        if (!hasAnyRole(ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin role required");
        }
    }

    public void requireSite(String siteId) {
        if (siteId == null || !siteIds.contains(siteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no access to this establishment");
        }
    }
}
