package ma.ensa.onboarding_service.dto;

import lombok.Builder;

import java.util.UUID;
@Builder
public record TenantOnboardingResponse(
        String orgName,
        String adminUsername,
        String orgId
) {


}
