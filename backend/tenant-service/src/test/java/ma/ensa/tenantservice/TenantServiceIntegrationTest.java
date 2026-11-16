package ma.ensa.tenantservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class TenantServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void noOp() {
        // The Flyway seed is intentionally immutable for these isolation checks.
    }

    @Test
    void tenantsReceiveDifferentBrandingAndCannotCrossRead() throws Exception {
        mvc.perform(get("/api/tenant/config")
                        .header("X-Auth-Subject", "agent-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-a"))
                .andExpect(jsonPath("$.config").value(containsString("#1FA38A")));

        mvc.perform(get("/api/tenant/config")
                        .header("X-Auth-Subject", "agent-b")
                        .header("X-Auth-Tenant", "tenant-b")
                        .header("X-Auth-Roles", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-b"))
                .andExpect(jsonPath("$.config").value(containsString("#D96C4F")));

        mvc.perform(get("/api/tenant/config/tenant-b")
                        .header("X-Auth-Subject", "agent-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "agent"))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/tenant/config/tenant-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Subject", "admin-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "tenant-admin")
                        .content("""
                                {"branding":{"name":"Cross tenant","primaryColor":"#112233","accentColor":"#445566"}}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTenantAndProviderAdminCannotUseTenantRoute() throws Exception {
        mvc.perform(get("/api/tenant/config")
                        .header("X-Auth-Subject", "provider")
                        .header("X-Auth-Roles", "provider-admin"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/tenant/config")
                        .header("X-Auth-Subject", "client"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantAdminUpdateValidatesAndPublicBrandingHasNoPrivateFields() throws Exception {
        mvc.perform(put("/api/tenant/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Subject", "admin-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "tenant-admin")
                        .content("""
                                {"branding":{"name":"Updated A","primaryColor":"#112233","accentColor":"#445566","logoUrl":"/logos/a.svg"}}
                                """))
                .andExpect(status().isOk());

        mvc.perform(put("/api/tenant/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Subject", "admin-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "tenant-admin")
                        .content("""
                                {"branding":{"name":"Bad","primaryColor":"red","accentColor":"#445566"}}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/public/tenants/tenant-a/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated A"))
                .andExpect(jsonPath("$.plan").doesNotExist())
                .andExpect(jsonPath("$.config").doesNotExist());

        mvc.perform(put("/api/tenant/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Auth-Subject", "admin-a")
                        .header("X-Auth-Tenant", "tenant-a")
                        .header("X-Auth-Roles", "tenant-admin")
                        .content("""
                                {"branding":{"name":"Clinique Al Amal","primaryColor":"#1FA38A","accentColor":"#F3B761"}}
                                """))
                .andExpect(status().isOk());
    }
}
