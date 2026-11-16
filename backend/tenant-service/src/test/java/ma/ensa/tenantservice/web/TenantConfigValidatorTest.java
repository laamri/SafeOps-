package ma.ensa.tenantservice.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantConfigValidatorTest {
    private final TenantConfigValidator validator = new TenantConfigValidator();

    @Test
    void acceptsSchemaWithSameOriginLogo() {
        assertDoesNotThrow(() -> validator.validate("""
                {"branding":{"name":"Tenant A","primaryColor":"#1FA38A","accentColor":"#F3B761","logoUrl":"/logos/a.svg"}}
                """));
    }

    @Test
    void rejectsInvalidColorAndExternalLogo() {
        assertThrows(RuntimeException.class, () -> validator.validate("""
                {"branding":{"name":"Tenant A","primaryColor":"red","accentColor":"#F3B761"}}
                """));
        assertThrows(RuntimeException.class, () -> validator.validate("""
                {"branding":{"name":"Tenant A","primaryColor":"#1FA38A","accentColor":"#F3B761","logoUrl":"https://evil.example/logo.svg"}}
                """));
    }
}
