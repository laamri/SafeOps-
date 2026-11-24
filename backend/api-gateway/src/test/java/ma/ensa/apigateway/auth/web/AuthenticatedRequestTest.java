package ma.ensa.apigateway.auth.web;

import ma.ensa.apigateway.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatedRequestTest {

    @Test
    void replacesClientSuppliedTenantHeadersWithTokenValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Org-Ids", "agency");
        request.addHeader("x-auth-site-ids", "agency");
        request.addHeader("X-Other", "kept");

        AuthenticatedRequest wrapped = new AuthenticatedRequest(request, user(Set.of("clinic")));

        assertEquals("clinic", wrapped.getHeader("X-Auth-Org-Ids"));
        assertEquals("clinic", wrapped.getHeader("x-auth-site-ids"));
        assertEquals(List.of("clinic"), Collections.list(wrapped.getHeaders("X-Auth-Org-Ids")));
        assertEquals("kept", wrapped.getHeader("X-Other"));
    }

    @Test
    void removesTenantHeadersWhenThereIsNoToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Auth-Org-Ids", "agency");
        request.addHeader("X-Auth-Site-Ids", "agency");

        AuthenticatedRequest wrapped = new AuthenticatedRequest(request, null);

        assertNull(wrapped.getHeader("X-Auth-Org-Ids"));
        assertNull(wrapped.getHeader("X-Auth-Site-Ids"));
        assertFalse(Collections.list(wrapped.getHeaderNames()).stream()
                .anyMatch(name -> name.equalsIgnoreCase("X-Auth-Org-Ids")
                        || name.equalsIgnoreCase("X-Auth-Site-Ids")));
    }

    @Test
    void omitsTenantHeadersWhenTheTokenHasNoGroups() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedRequest wrapped = new AuthenticatedRequest(request, user(Set.of()));

        assertNull(wrapped.getHeader("X-Auth-Org-Ids"));
        assertNull(wrapped.getHeader("X-Auth-Site-Ids"));
    }

    @Test
    void serializesMultipleSitesInSortedOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedRequest wrapped = new AuthenticatedRequest(request, user(Set.of("clinic", "agency")));

        assertEquals("agency,clinic", wrapped.getHeader("X-Auth-Site-Ids"));
    }

    @Test
    void listsManagedHeadersInTheirCanonicalCase() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthenticatedRequest wrapped = new AuthenticatedRequest(request, user(Set.of("clinic")));

        List<String> names = Collections.list(wrapped.getHeaderNames());
        assertTrue(names.contains("X-Auth-Org-Ids"));
        assertTrue(names.contains("X-Auth-Site-Ids"));
    }

    private static AuthenticatedUser user(Set<String> sites) {
        return new AuthenticatedUser(
                "subject", "agent", null, "frontend",
                Set.of("agent"), Map.of(),
                sites, sites,
                0L);
    }
}
