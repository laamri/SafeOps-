package ma.ensa.staffadmin.web;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import ma.ensa.staffadmin.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The service must validate the token itself and authorise before touching Keycloak. Every
 * rejection below also asserts that the Keycloak client was never called.
 */
@SpringBootTest
class StaffAuthorizationTest {

    private static final String ISSUER = "http://issuer.test/realms/tiko";
    private static final String AUDIENCE = "tiko-api";
    private static final String KID = "test-key";
    private static final KeyPair KEYS = generateKeys();
    private static final KeyPair OTHER_KEYS = generateKeys();

    static {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey) KEYS.getPublic())
                    .keyID(KID).build()).toString();
            server.createContext("/certs", exchange -> {
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            JWKS_PORT = server.getAddress().getPort();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot start the test JWKS server", exception);
        }
    }

    private static int JWKS_PORT;

    @DynamicPropertySource
    static void staffAdminProperties(DynamicPropertyRegistry registry) {
        registry.add("staff-admin.security.issuer", () -> ISSUER);
        registry.add("staff-admin.security.jwk-set-uri", () -> "http://localhost:" + JWKS_PORT + "/certs");
        registry.add("staff-admin.security.audience", () -> AUDIENCE);
    }

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void forgedAuthHeadersWithoutTokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("X-Auth-Subject", "admin-1")
                        .header("X-Auth-Roles", "admin")
                        .header("X-Auth-Sites", "clinic"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(keycloak);
    }

    @Test
    void tokenOfAnotherTenantIsForbidden() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("agency-admin", List.of("admin"),
                                List.of("/tenants/agency"), AUDIENCE, Instant.now().plusSeconds(300), KEYS))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(keycloak);
    }

    @Test
    void agentTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("agent-1", List.of("agent"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().plusSeconds(300), KEYS))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(keycloak);
    }

    @Test
    void agentCannotInviteEvenAtItsOwnSite() throws Exception {
        mockMvc.perform(post("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("agent-1", List.of("agent"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().plusSeconds(300), KEYS)))
                        .contentType(APPLICATION_JSON)
                        .content(inviteBody()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(keycloak);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().minusSeconds(600), KEYS))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(keycloak);
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of("/tenants/clinic"), "another-api", Instant.now().plusSeconds(300), KEYS))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(keycloak);
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().plusSeconds(300), OTHER_KEYS))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(keycloak);
    }

    @Test
    void adminWithoutSiteIsForbidden() throws Exception {
        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of(), AUDIENCE, Instant.now().plusSeconds(300), KEYS))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(keycloak);
    }

    @Test
    void clinicAdminCanListClinicStaff() throws Exception {
        when(keycloak.listStaff("clinic")).thenReturn(List.of(
                new StaffView("id-1", "agent", "agent@tiko.test", "Mehdi", "Karim", true)));

        mockMvc.perform(get("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().plusSeconds(300), KEYS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("agent"));

        verify(keycloak).listStaff("clinic");
    }

    @Test
    void clinicAdminInvitationReturnsTheTemporaryPasswordOnce() throws Exception {
        when(keycloak.inviteAgent(eq("clinic"), any(InviteStaffRequest.class)))
                .thenReturn(new InvitationView("id-2", "new.agent", "TempPass-aZ7!"));

        mockMvc.perform(post("/api/staff/sites/clinic/staff")
                        .header("Authorization", bearer(token("admin-1", List.of("admin"),
                                List.of("/tenants/clinic"), AUDIENCE, Instant.now().plusSeconds(300), KEYS)))
                        .contentType(APPLICATION_JSON)
                        .content(inviteBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new.agent"))
                .andExpect(jsonPath("$.temporaryPassword", not(blankOrNullString())));

        verify(keycloak).inviteAgent(eq("clinic"), any(InviteStaffRequest.class));
    }

    private static String inviteBody() {
        return """
                {"username":"new.agent","email":"new.agent@tiko.test","firstName":"New","lastName":"Agent"}
                """;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String subject, List<String> roles, List<String> groups, String audience,
                                Instant expiresAt, KeyPair signingKeys) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(120)))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("realm_access", Map.of("roles", roles))
                .claim("groups", groups)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) signingKeys.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("cannot generate an RSA key pair", exception);
        }
    }
}
