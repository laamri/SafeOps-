package ma.ensa.onboarding_service.config;


import ma.ensa.onboarding_service.HttpClient;
import ma.ensa.onboarding_service.properties.KeycloakProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class KeycloakRestConfig {
    @Bean
    HttpClient KeycloakRestClient(KeycloakProperties properties){
        RestClient restClient =  RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
        HttpExchangeAdapter exchangeAdapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory.builderFor(exchangeAdapter).build();
        return proxyFactory.createClient(HttpClient.class);

    }

}
