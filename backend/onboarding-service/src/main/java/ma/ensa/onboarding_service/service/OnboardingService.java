package ma.ensa.onboarding_service.service;


import ma.ensa.onboarding_service.dto.TenantOnboardingRequest;
import ma.ensa.onboarding_service.dto.TenantOnboardingResponse;
import ma.ensa.onboarding_service.model.OrganizationRepresentation;

public interface OnboardingService {


    void deleteOrg();
    void inviteUsers();

    TenantOnboardingResponse onboard(TenantOnboardingRequest request);

}
