package ma.ensa.onboarding_service.dto;

import jakarta.validation.constraints.Email;

public record TenantOnboardingRequest(
        String subdomain,
        String orgName,
        String adminUsername,
        String adminPassword,
        @Email
        String email,
        String domain

) {
}
