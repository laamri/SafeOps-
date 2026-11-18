package ma.ensa.onboarding_service.model;

import ma.ensa.onboarding_service.dto.TenantOnboardingRequest;
import ma.ensa.onboarding_service.dto.TenantOnboardingResponse;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record OnboardingContext(
        OrganizationRepresentation org,
        TenantOnboardingRequest tenantRequest
) {
    // static factory method
    public static OnboardingContext of(TenantOnboardingRequest tenantRequest){
        return new OnboardingContext(null, tenantRequest);
    }

    //
    public OnboardingContext validateAndReserve(Consumer<String> consumer){
        consumer.accept(tenantRequest.domain());
        return  this;
    }
    public OnboardingContext prepareOrg(){
        var org = OrganizationRepresentation.from(this.tenantRequest);
        return new OnboardingContext(org,this.tenantRequest);
    }


    public OnboardingContext provisionInKeycloak(BiFunction<String, OrganizationRepresentation, ResponseEntity<Void>> function,
                                                 Supplier<String> jwtSupplier){
        var token = jwtSupplier.get();
        var location = function.apply(token, this.org()).getHeaders().getLocation();
        var uriComponents = UriComponentsBuilder.fromUri(location).build();
        var orgId = uriComponents.getPathSegments().getLast();
        return  new OnboardingContext(this.org.withOrgId(orgId),this.tenantRequest());

    }
    public TenantOnboardingResponse toResponse(){
        return TenantOnboardingResponse.builder()
                .adminUsername(this.tenantRequest().adminUsername())
                .orgName(this.org.name())
                .orgId(this.org.orgId())
                .build();
    }



}
