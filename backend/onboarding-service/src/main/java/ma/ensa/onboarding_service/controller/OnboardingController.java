package ma.ensa.onboarding_service.controller;


import ma.ensa.onboarding_service.dto.TenantOnboardingRequest;
import ma.ensa.onboarding_service.dto.TenantOnboardingResponse;
import ma.ensa.onboarding_service.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
    //The service is a singleton, meaning it s shared and accessible by all threads
    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping()
    ResponseEntity<TenantOnboardingResponse> submitForm(@RequestBody TenantOnboardingRequest tenantRequest){

            return ResponseEntity.ok(onboardingService.onboard(tenantRequest));
    }
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteOrg(@PathVariable String id){
        return ResponseEntity.ok().build();
    }
}
