package ma.ensa.apigateway.auth.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAdapterTest {

    @Test
    void decodesTheGroupsClaimFromThePayload() {
        String token = token("""
                {
                  "sub": "subject",
                  "aud": "tiko-api",
                  "groups": ["/tenants/clinic"],
                  "realm_access": {"roles": ["agent"]}
                }
                """);

        Jwt jwt = new JwtAdapter().buildJwt(token);

        assertEquals(Set.of("/tenants/clinic"), jwt.payload().groups());
        assertEquals(Set.of("tiko-api"), jwt.payload().aud());
    }

    private static String token(String payload) {
        return base64("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"kid\"}")
                + "." + base64(payload) + ".c2ln";
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
