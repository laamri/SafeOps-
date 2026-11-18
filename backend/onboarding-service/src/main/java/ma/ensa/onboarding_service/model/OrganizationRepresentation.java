package ma.ensa.onboarding_service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.With;
import ma.ensa.onboarding_service.dto.TenantOnboardingRequest;


import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrganizationRepresentation(
        @JsonProperty(value = "id", access = JsonProperty.Access.READ_ONLY)
        @With
        String orgId,
        @JsonProperty("name")
        String name,
        String alias,
        String description,
        boolean enabled,
        List<OrgaDomainRepresentation> domains,
        Map<String,List<String>> attributes

) {
    @Builder
    public record OrgaDomainRepresentation(
            String name,
            boolean verified
    ){}
    //static factory method
    public static OrganizationRepresentation from(TenantOnboardingRequest request){
        var domain = new OrgaDomainRepresentation(request.domain(), false);
        return OrganizationRepresentation.builder()
                .domains(List.of(domain))
                .enabled(true)
                .name(request.orgName())
                .build();
    }
}
