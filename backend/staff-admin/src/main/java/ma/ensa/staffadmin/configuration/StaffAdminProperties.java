package ma.ensa.staffadmin.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "staff-admin")
public record StaffAdminProperties(Keycloak keycloak, Security security) {

    public record Keycloak(String baseUrl, String realm, String clientId, String clientSecret) {
    }

    public record Security(String issuer, String jwkSetUri, String audience) {
    }
}
