package ma.ensa.staffadmin.keycloak;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ma.ensa.staffadmin.configuration.StaffAdminProperties;
import ma.ensa.staffadmin.web.InvitationView;
import ma.ensa.staffadmin.web.InviteStaffRequest;
import ma.ensa.staffadmin.web.MemberUpdateRequest;
import ma.ensa.staffadmin.web.StaffView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP calls against a stub Keycloak. It proves that an invited account gets
 * exactly the agent role and the caller's site group, and that a user of another site is never
 * touched: the membership check happens before any write.
 */
class KeycloakAdminClientTest {

    private static final String CLINIC_USER = "22222222-2222-2222-2222-222222222222";
    private static final String AGENCY_USER = "11111111-1111-1111-1111-111111111111";
    private static final String NEW_USER = "33333333-3333-3333-3333-333333333333";
    private static final String ADMIN = "/admin/realms/tiko";

    private HttpServer server;
    private KeycloakAdminClient client;
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startStubKeycloak() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new KeycloakAdminClient(new StaffAdminProperties(
                new StaffAdminProperties.Keycloak(baseUrl, "tiko", "tiko-staff-admin", "test-secret"),
                new StaffAdminProperties.Security("http://issuer.test/realms/tiko", baseUrl + "/certs", "tiko-api")));
    }

    @AfterEach
    void stopStubKeycloak() {
        server.stop(0);
    }

    @Test
    void invitationAssignsOnlyTheAgentRoleAndTheCallersSiteGroup() throws Exception {
        InvitationView invitation = client.inviteAgent("clinic",
                new InviteStaffRequest("new.agent", "new.agent@tiko.test", "New", "Agent"));

        assertNotNull(invitation.temporaryPassword());
        Request created = only("POST", ADMIN + "/users");
        String user = created.body();
        assertFalse(user.contains("\"role\""));
        assertFalse(user.contains("\"roles\""));
        assertFalse(user.contains("\"groups\""));
        assertFalse(user.contains("\"group\""));
        assertTrue(user.contains("\"temporary\":true"));
        assertTrue(user.contains("\"requiredActions\":[\"UPDATE_PASSWORD\"]"));

        Request mapping = only("POST", ADMIN + "/users/" + NEW_USER + "/role-mappings/realm");
        assertTrue(mapping.body().contains("\"name\":\"agent\""));
        assertFalse(mapping.body().contains("admin"));

        Request joined = only("PUT", ADMIN + "/users/" + NEW_USER + "/groups/clinic-group");
        assertEquals("PUT", joined.method());
    }

    @Test
    void anAgencyUserIsUnknownAtTheClinicAndIsNeverWritten() {
        for (Runnable operation : List.<Runnable>of(
                () -> client.setEnabled("clinic", AGENCY_USER, false),
                () -> client.updateMember("clinic", AGENCY_USER,
                        new MemberUpdateRequest("agency@tiko.test", "A", "B")),
                () -> client.removeMember("clinic", AGENCY_USER),
                () -> client.resetPassword("clinic", AGENCY_USER))) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, operation::run);
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }

        assertTrue(requests.stream()
                .filter(request -> request.path().startsWith(ADMIN + "/users/" + AGENCY_USER))
                .allMatch(request -> request.method().equals("GET")),
                "only the membership read may reach another site's user");
    }

    @Test
    void aClinicMemberCanBeDisabledUpdatedRemovedAndReset() {
        StaffView disabled = client.setEnabled("clinic", CLINIC_USER, false);
        assertEquals(CLINIC_USER, disabled.id());
        assertTrue(only("PUT", ADMIN + "/users/" + CLINIC_USER).body().contains("\"enabled\":false"));

        StaffView updated = client.updateMember("clinic", CLINIC_USER,
                new MemberUpdateRequest("member@tiko.test", "New", "Name"));
        assertEquals(CLINIC_USER, updated.id());
        assertTrue(last("PUT", ADMIN + "/users/" + CLINIC_USER).body().contains("member@tiko.test"));

        client.removeMember("clinic", CLINIC_USER);
        assertEquals(1, requests.stream().filter(request ->
                request.method().equals("DELETE") && request.path().equals(ADMIN + "/users/" + CLINIC_USER)).count());

        InvitationView reset = client.resetPassword("clinic", CLINIC_USER);
        assertTrue(reset.temporaryPassword().length() >= 16);
        assertTrue(last("PUT", ADMIN + "/users/" + CLINIC_USER + "/reset-password").body()
                .contains("\"temporary\":true"));
        assertTrue(last("PUT", ADMIN + "/users/" + CLINIC_USER).body().contains("UPDATE_PASSWORD"));
    }

    private Request only(String method, String path) {
        List<Request> matching = requests.stream()
                .filter(request -> request.method().equals(method) && request.path().equals(path))
                .toList();
        assertEquals(1, matching.size(), "expected one " + method + " " + path + " but got " + matching);
        return matching.getFirst();
    }

    private Request last(String method, String path) {
        return requests.reversed().stream()
                .filter(request -> request.method().equals(method) && request.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + method + " " + path));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(method, path, query, body));

        if (path.equals("/realms/tiko/protocol/openid-connect/token")) {
            send(exchange, 200, "{\"access_token\":\"test-admin-token\",\"expires_in\":300}");
            return;
        }
        if (!path.startsWith(ADMIN)) {
            send(exchange, 404, "{}");
            return;
        }
        String suffix = path.substring(ADMIN.length());
        if (suffix.equals("/groups")) {
            send(exchange, 200, "[{\"id\":\"tenants-group\",\"path\":\"/tenants\"}]");
            return;
        }
        if (suffix.equals("/groups/tenants-group/children")) {
            send(exchange, 200, """
                    [{"id":"clinic-group","path":"/tenants/clinic"},
                     {"id":"agency-group","path":"/tenants/agency"}]
                    """);
            return;
        }
        if (suffix.equals("/roles/agent")) {
            send(exchange, 200, "{\"id\":\"role-agent\",\"name\":\"agent\"}");
            return;
        }
        if (suffix.equals("/users") && method.equals("POST")) {
            send(exchange, 201, "{}");
            return;
        }
        if (suffix.equals("/users")) {
            send(exchange, 200, "[{\"id\":\"" + NEW_USER + "\",\"username\":\"new.agent\","
                    + "\"email\":\"new.agent@tiko.test\",\"firstName\":\"New\",\"lastName\":\"Agent\","
                    + "\"enabled\":true}]");
            return;
        }
        if (suffix.equals("/users/" + AGENCY_USER + "/groups")) {
            send(exchange, 200, "[{\"id\":\"agency-group\",\"path\":\"/tenants/agency\"}]");
            return;
        }
        if (suffix.equals("/users/" + CLINIC_USER + "/groups")
                || suffix.equals("/users/" + NEW_USER + "/groups")) {
            send(exchange, 200, "[{\"id\":\"clinic-group\",\"path\":\"/tenants/clinic\"}]");
            return;
        }
        if (suffix.equals("/users/" + CLINIC_USER) && method.equals("GET")
                || suffix.equals("/users/" + NEW_USER) && method.equals("GET")) {
            send(exchange, 200, user(CLINIC_USER, "member", true));
            return;
        }
        if (suffix.equals("/users/" + CLINIC_USER) && method.equals("PUT")
                || suffix.equals("/users/" + NEW_USER) && method.equals("PUT")) {
            send(exchange, 204, null);
            return;
        }
        if (suffix.equals("/users/" + CLINIC_USER) && method.equals("DELETE")) {
            send(exchange, 204, null);
            return;
        }
        if (suffix.equals("/users/" + CLINIC_USER + "/reset-password")
                || suffix.equals("/users/" + NEW_USER + "/reset-password")
                || suffix.equals("/users/" + NEW_USER + "/role-mappings/realm")
                || suffix.equals("/users/" + NEW_USER + "/groups/clinic-group")) {
            send(exchange, 204, null);
            return;
        }
        send(exchange, 404, "{}");
    }

    private static String user(String id, String username, boolean enabled) {
        return "{\"id\":\"" + id + "\",\"username\":\"" + username + "\",\"email\":\"" + username
                + "@tiko.test\",\"firstName\":\"First\",\"lastName\":\"Last\",\"enabled\":" + enabled + "}";
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        if (json == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private record Request(String method, String path, String query, String body) {
    }
}
