package ma.ensa.onboarding_service;

import ma.ensa.onboarding_service.dto.KeycloakTokenResponse;
import ma.ensa.onboarding_service.model.OrganizationRepresentation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;


@HttpExchange()
public interface HttpClient {


    @PostExchange(url = "admin/realms/master/organizations")
    ResponseEntity<Void>  createOrg(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String jwt,
            @RequestBody OrganizationRepresentation organizationRepresentation);
    @PostExchange(url = "realms/master/protocol/openid-connect/token", contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<KeycloakTokenResponse> getJwt(@RequestBody MultiValueMap<String,String> map);
    @DeleteExchange(url = "admin/realms/master/organizations/{id}")
    ResponseEntity<Void> deleteOrg(@RequestHeader(HttpHeaders.AUTHORIZATION) String jwt);




}
