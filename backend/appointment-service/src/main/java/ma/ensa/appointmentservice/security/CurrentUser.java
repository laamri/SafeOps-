package ma.ensa.appointmentservice.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * The gateway has already verified the token and stripped any X-Auth-* headers the client sent;
 * this service only trusts the values it forwards. In Slice 1 an establishment's organisation id
 * is also its site id, so staff operations require the site: a staff token without one is refused,
 * never widened to "all sites".
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

    public void requireSite(String siteId) {
        if (siteId == null || !siteIds.contains(siteId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no access to this establishment");
        }
    }
}
