package ma.ensa.staffadmin.keycloak;

import ma.ensa.staffadmin.configuration.StaffAdminProperties;
import ma.ensa.staffadmin.web.InvitationView;
import ma.ensa.staffadmin.web.InviteStaffRequest;
import ma.ensa.staffadmin.web.StaffView;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Calls the Keycloak admin API with the service account of the dedicated tiko-staff-admin
 * client. Only reached after the caller was authorised for the requested site.
 */
@Component
public class KeycloakAdminClient {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final String GROUP_PREFIX = "/tenants/";

    private final StaffAdminProperties properties;
    private final RestClient http = RestClient.create();
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public KeycloakAdminClient(StaffAdminProperties properties) {
        this.properties = properties;
    }

    public List<StaffView> listStaff(String site) {
        String token = adminToken();
        String groupId = groupId(site, token);
        try {
            List<Map<String, Object>> members = getList(token,
                    "/groups/" + groupId + "/members?briefRepresentation=false&max=200");
            return members.stream().map(KeycloakAdminClient::toView).toList();
        } catch (RestClientException exception) {
            throw failure(exception);
        }
    }

    public InvitationView inviteAgent(String site, InviteStaffRequest request) {
        String token = adminToken();
        String groupId = groupId(site, token);
        String temporaryPassword = temporaryPassword();

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("username", request.username());
        user.put("email", request.email());
        user.put("firstName", request.firstName());
        user.put("lastName", request.lastName());
        user.put("enabled", true);
        user.put("emailVerified", false);
        user.put("credentials", List.of(Map.of(
                "type", "password", "value", temporaryPassword, "temporary", true)));
        user.put("requiredActions", List.of("UPDATE_PASSWORD"));

        String userId;
        try {
            http.post()
                    .uri(adminUri("/users"))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
            userId = userId(token, request.username());
        } catch (RestClientResponseException conflict) {
            if (conflict.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "username already exists");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "keycloak admin call failed");
        } catch (RestClientException exception) {
            throw failure(exception);
        }

        try {
            assignAgentRole(token, userId);
            joinGroup(token, userId, groupId);
        } catch (RestClientException exception) {
            deleteUserQuietly(token, userId);
            throw failure(exception);
        }
        return new InvitationView(userId, request.username(), temporaryPassword);
    }

    private void assignAgentRole(String token, String userId) {
        Map<String, Object> role = getMap(token, "/roles/agent");
        List<Map<String, Object>> mapping = List.of(Map.of("id", role.get("id"), "name", role.get("name")));
        http.post()
                .uri(adminUri("/users/" + userId + "/role-mappings/realm"))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapping)
                .retrieve()
                .toBodilessEntity();
    }

    private void joinGroup(String token, String userId, String groupId) {
        http.put()
                .uri(adminUri("/users/" + userId + "/groups/" + groupId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .toBodilessEntity();
    }

    private void deleteUserQuietly(String token, String userId) {
        try {
            http.delete()
                    .uri(adminUri("/users/" + userId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // best effort: the invitation already failed and is reported as such
        }
    }

    private String userId(String token, String username) {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        List<Map<String, Object>> users = getList(token, "/users?username=" + encoded + "&exact=true&max=1");
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "keycloak did not return the created user");
        }
        return String.valueOf(users.getFirst().get("id"));
    }

    private String groupId(String site, String token) {
        // the tenant groups are children of /tenants: newer Keycloak returns an empty subGroups
        // list on the top-level groups call, so the children endpoint is the reliable read
        List<Map<String, Object>> top = getList(token, "/groups?search=tenants&max=100");
        String tenantsId = top.stream()
                .filter(group -> GROUP_PREFIX.substring(0, GROUP_PREFIX.length() - 1).equals(group.get("path")))
                .map(group -> String.valueOf(group.get("id")))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown site " + site));
        String wanted = GROUP_PREFIX + site;
        return getList(token, "/groups/" + tenantsId + "/children?max=100").stream()
                .filter(group -> wanted.equals(group.get("path")))
                .map(group -> String.valueOf(group.get("id")))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown site " + site));
    }

    private Map<String, Object> getMap(String token, String path) {
        Map<String, Object> body = http.get()
                .uri(adminUri(path))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
        return body == null ? Map.of() : body;
    }

    private List<Map<String, Object>> getList(String token, String path) {
        List<Map<String, Object>> body = http.get()
                .uri(adminUri(path))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
        return body == null ? List.of() : new ArrayList<>(body);
    }

    private String adminToken() {
        CachedToken current = cachedToken.get();
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.value();
        }
        StaffAdminProperties.Keycloak keycloak = properties.keycloak();
        if (keycloak.clientSecret() == null || keycloak.clientSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "staff-admin keycloak client is not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloak.clientId());
        form.add("client_secret", keycloak.clientSecret());
        try {
            Map<String, Object> response = http.post()
                    .uri(keycloak.baseUrl() + "/realms/" + keycloak.realm()
                            + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null || response.get("access_token") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "keycloak admin call failed");
            }
            long lifetime = ((Number) response.getOrDefault("expires_in", 30)).longValue();
            CachedToken refreshed = new CachedToken(
                    String.valueOf(response.get("access_token")),
                    Instant.now().plusSeconds(Math.max(5, lifetime - 15)));
            cachedToken.set(refreshed);
            return refreshed.value();
        } catch (RestClientException exception) {
            throw failure(exception);
        }
    }

    private String adminUri(String path) {
        return properties.keycloak().baseUrl() + "/admin/realms/" + properties.keycloak().realm() + path;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static ResponseStatusException failure(RestClientException exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "keycloak admin call failed", exception);
    }

    private static StaffView toView(Map<String, Object> user) {
        return new StaffView(
                text(user, "id"),
                text(user, "username"),
                text(user, "email"),
                text(user, "firstName"),
                text(user, "lastName"),
                Boolean.TRUE.equals(user.get("enabled")));
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String temporaryPassword() {
        StringBuilder password = new StringBuilder(24);
        for (int i = 0; i < 20; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        // guarantee the classes a stricter password policy may require
        password.append("aZ7!");
        return password.toString();
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
