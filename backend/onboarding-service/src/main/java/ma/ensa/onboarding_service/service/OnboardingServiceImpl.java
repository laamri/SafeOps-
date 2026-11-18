package ma.ensa.onboarding_service.service;

import ma.ensa.onboarding_service.HttpClient;
import ma.ensa.onboarding_service.dto.KeycloakTokenResponse;
import ma.ensa.onboarding_service.dto.TenantOnboardingRequest;
import ma.ensa.onboarding_service.dto.TenantOnboardingResponse;
import ma.ensa.onboarding_service.model.OnboardingContext;
import ma.ensa.onboarding_service.model.OrganizationRepresentation;
import ma.ensa.onboarding_service.properties.KeycloakProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;


import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class OnboardingServiceImpl implements OnboardingService {
    private final List<String> reservedSubdomains = new ArrayList<>();
    private final HttpClient httpClient;
    private final KeycloakProperties properties;



    public OnboardingServiceImpl(HttpClient httpClient, KeycloakProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;

    }





    @Override
    public void deleteOrg() {
        httpClient.deleteOrg(getJwt());
    }

    @Override
    public void inviteUsers() {

    }

    private void setDomain(String subdomain) {
       if (subdomain!=null){
        reservedSubdomains.add(subdomain);}
       else throw new RuntimeException("subdomain not valid");

    }
    private String getJwt(){
       KeycloakTokenResponse jwt =  httpClient.getJwt(properties.toFormData()).getBody();
       return "Bearer "+jwt.accessToken();
    }

    @Override
    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {

        return OnboardingContext.of(request)
                .validateAndReserve(s->reservedSubdomains.add(s))
                .prepareOrg()
                .provisionInKeycloak((token,org)->httpClient.createOrg(token,org), ()->getJwt())
                .toResponse();



    }


}
