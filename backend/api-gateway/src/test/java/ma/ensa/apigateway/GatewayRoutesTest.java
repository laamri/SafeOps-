package ma.ensa.apigateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the explicit gateway routes: every path the frontend calls must be mapped to its service
 * by a named route, and nothing may fall back to a catch-all.
 */
class GatewayRoutesTest {

    private static final List<String> FRONTEND_PATHS = List.of(
            "/api/establishments",
            "/api/establishments/clinic",
            "/api/tickets",
            "/api/tickets/me",
            "/api/tickets/ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f",
            "/api/tickets/ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f/cancel",
            "/api/queues/1/counter",
            "/api/queues/1/call-next",
            "/api/queues/1/status",
            "/api/tickets/ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f/served",
            "/api/tickets/ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f/no-show",
            "/api/appointments/me",
            "/api/appointments",
            "/api/appointments/ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f/cancel",
            "/api/profile/preferences",
            "/api/notifications",
            "/api/notifications/1/read",
            "/api/staff/sites/clinic/staff");

    private static final Map<String, String> EXPECTED_SERVICE_BY_ROUTE = Map.of(
            "appointment-service", "appointment",
            "profile-notification-service-profile", "profile",
            "profile-notification-service-notifications", "notifications",
            "queue-establishments", "queue",
            "queue-tickets", "queue",
            "queue-queues", "queue");

    private static Map<String, String> uriByRoute;
    private static Map<String, List<String>> patternsByRoute;

    @BeforeAll
    static void parseRouteConfiguration() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = GatewayRoutesTest.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        Map<Integer, String> ids = new TreeMap<>();
        Map<Integer, String> uris = new TreeMap<>();
        Map<Integer, String> predicates = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            String prefix = "spring.cloud.gateway.server.webmvc.routes[";
            if (!name.startsWith(prefix)) {
                continue;
            }
            int close = name.indexOf(']', prefix.length());
            int index = Integer.parseInt(name.substring(prefix.length(), close));
            String rest = name.substring(close + 2);
            String value = properties.getProperty(name);
            switch (rest) {
                case "id" -> ids.put(index, value);
                case "uri" -> uris.put(index, value);
                case "predicates[0]" -> predicates.put(index, value);
                default -> {
                }
            }
        }
        uriByRoute = new LinkedHashMap<>();
        patternsByRoute = new LinkedHashMap<>();
        for (Integer index : ids.keySet()) {
            String id = ids.get(index);
            uriByRoute.put(id, uris.get(index));
            String predicate = predicates.get(index);
            assertTrue(predicate.startsWith("Path="), "route " + id + " must be path-routed");
            patternsByRoute.put(id, List.of(predicate.substring("Path=".length()).split(",")));
        }
    }

    @Test
    void everyFrontendEndpointHasAnExplicitRoute() {
        for (String path : FRONTEND_PATHS) {
            assertFalse(
                    patternsByRoute.entrySet().stream()
                            .filter(route -> route.getValue().stream().anyMatch(pattern -> matches(pattern, path)))
                            .toList().isEmpty(),
                    "no gateway route matches frontend path " + path);
        }
    }

    @Test
    void routesGoToTheExpectedServices() {
        assertEquals(Set.of("appointment-service", "profile-notification-service-profile",
                        "profile-notification-service-notifications", "queue-establishments", "queue-tickets",
                        "queue-queues", "staff-admin"),
                uriByRoute.keySet());
        assertTrue(uriByRoute.get("appointment-service").contains("APPOINTMENT_SERVICE_URL"));
        assertTrue(uriByRoute.get("profile-notification-service-profile").contains("PROFILE_SERVICE_URL"));
        assertTrue(uriByRoute.get("profile-notification-service-notifications").contains("PROFILE_SERVICE_URL"));
        assertTrue(uriByRoute.get("queue-establishments").contains("QUEUE_SERVICE_URL"));
        assertTrue(uriByRoute.get("queue-tickets").contains("QUEUE_SERVICE_URL"));
        assertTrue(uriByRoute.get("queue-queues").contains("QUEUE_SERVICE_URL"));
        assertTrue(uriByRoute.get("staff-admin").contains("STAFF_ADMIN_URL"));
    }

    @Test
    void thereIsNoApiCatchAll() {
        assertFalse(allPatterns().contains("/api/**"));
        assertFalse(allPatterns().stream().anyMatch(pattern -> pattern.endsWith("/api/**")));
    }

    @Test
    void basePathsHaveTheirOwnPatterns() {
        assertEquals(Set.of("/api/tickets", "/api/tickets/**"), Set.copyOf(patternsByRoute.get("queue-tickets")));
        assertEquals(Set.of("/api/establishments", "/api/establishments/**"),
                Set.copyOf(patternsByRoute.get("queue-establishments")));
        assertEquals(Set.of("/api/appointments", "/api/appointments/**"),
                Set.copyOf(patternsByRoute.get("appointment-service")));
        assertEquals(Set.of("/api/staff", "/api/staff/**"), Set.copyOf(patternsByRoute.get("staff-admin")));
    }

    private static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return pattern.equals(path);
    }

    private static List<String> allPatterns() {
        List<String> all = new ArrayList<>();
        patternsByRoute.values().forEach(all::addAll);
        return all;
    }
}
