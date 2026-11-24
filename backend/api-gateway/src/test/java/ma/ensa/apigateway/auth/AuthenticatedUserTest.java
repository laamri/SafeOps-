package ma.ensa.apigateway.auth;

import ma.ensa.apigateway.auth.util.Jwt;
import ma.ensa.apigateway.auth.util.JwtHeader;
import ma.ensa.apigateway.auth.util.JwtPayload;
import ma.ensa.apigateway.auth.util.RealmAccess;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedUserTest {

    @Test
    void readsFlatTenantGroupAsOrganizationAndSite() {
        AuthenticatedUser user = userWithGroups(Set.of("/tenants/clinic"));

        assertEquals(Set.of("clinic"), user.orgIds());
        assertEquals(Set.of("clinic"), user.siteIds());
        assertTrue(user.hasOrganization("clinic"));
        assertTrue(user.hasSite("clinic"));
    }

    @Test
    void readsNestedOrganizationAndSiteGroup() {
        AuthenticatedUser user = userWithGroups(Set.of("/tenants/clinic/sites/agency"));

        assertEquals(Set.of("clinic"), user.orgIds());
        assertEquals(Set.of("agency"), user.siteIds());
        assertFalse(user.hasSite("clinic"));
    }

    @Test
    void ignoresGroupsThatAreNotTenantGroups() {
        AuthenticatedUser user = userWithGroups(Set.of("/staff", "/tenants", "/other/clinic"));

        assertTrue(user.orgIds().isEmpty());
        assertTrue(user.siteIds().isEmpty());
    }

    @Test
    void handlesMissingGroupsClaim() {
        AuthenticatedUser user = userWithGroups(null);

        assertTrue(user.orgIds().isEmpty());
        assertTrue(user.siteIds().isEmpty());
        assertFalse(user.hasSite("clinic"));
    }

    private static AuthenticatedUser userWithGroups(Set<String> groups) {
        JwtPayload payload = new JwtPayload(
                "subject", "issuer", null, null, null, null,
                "Bearer", "frontend", null, null,
                Set.of("tiko-api"), null, "agent", null,
                groups,
                new RealmAccess(Set.of("agent")),
                Map.of());
        return AuthenticatedUser.from(new Jwt(
                new JwtHeader("RS256", "JWT", "kid"), payload, new byte[0], "header.payload"));
    }
}
