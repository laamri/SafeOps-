package ma.ensa.staffadmin.web;

import jakarta.validation.Valid;
import ma.ensa.staffadmin.keycloak.KeycloakAdminClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Site comes from the caller's own token group (/tenants/{site}); there is no way to act on
 * another tenant through a header or parameter. The admin role and the site are checked here,
 * before Keycloak is contacted.
 */
@RestController
@RequestMapping("/api/staff/sites/{site}/staff")
public class StaffController {

    private static final Pattern SITE = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    private final KeycloakAdminClient keycloak;

    public StaffController(KeycloakAdminClient keycloak) {
        this.keycloak = keycloak;
    }

    @GetMapping
    public List<StaffView> list(@PathVariable String site, JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        return keycloak.listStaff(site);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationView invite(@PathVariable String site, @Valid @RequestBody InviteStaffRequest request,
                                 JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        return keycloak.inviteAgent(site, request);
    }

    /**
     * Every user-id operation is scoped twice: the caller must be an admin of the site named in
     * the path, and the target must be a member of that same site group. A user of another site
     * is reported as unknown (404), like a random id, so ids cannot be probed across tenants.
     */
    @PutMapping("/{userId}/enabled")
    public StaffView setEnabled(@PathVariable String site, @PathVariable String userId,
                                @Valid @RequestBody MemberEnabledRequest request,
                                JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        requireUserId(userId);
        return keycloak.setEnabled(site, userId, request.enabled());
    }

    @PutMapping("/{userId}")
    public StaffView update(@PathVariable String site, @PathVariable String userId,
                            @Valid @RequestBody MemberUpdateRequest request,
                            JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        requireUserId(userId);
        return keycloak.updateMember(site, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String site, @PathVariable String userId,
                       JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        requireUserId(userId);
        keycloak.removeMember(site, userId);
    }

    @PostMapping("/{userId}/reset-password")
    public InvitationView resetPassword(@PathVariable String site, @PathVariable String userId,
                                        JwtAuthenticationToken caller) {
        requireAdminAtSite(caller.getToken(), site);
        requireUserId(userId);
        return keycloak.resetPassword(site, userId);
    }

    static void requireUserId(String userId) {
        try {
            UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown member");
        }
    }

    static void requireAdminAtSite(Jwt token, String site) {
        if (!SITE.matcher(site).matches() || !roles(token).contains("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin role required");
        }
        if (!sites(token).contains(site)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no access to this site");
        }
    }

    static List<String> roles(Jwt token) {
        Object realmAccess = token.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return List.of();
    }

    static List<String> sites(Jwt token) {
        List<String> groups = token.getClaimAsStringList("groups");
        if (groups == null) {
            return List.of();
        }
        return groups.stream()
                .filter(group -> group.startsWith("/tenants/") && group.indexOf('/', "/tenants/".length()) < 0)
                .map(group -> group.substring("/tenants/".length()))
                .toList();
    }
}
