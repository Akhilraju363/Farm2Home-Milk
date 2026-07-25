package com.farm2home.observability.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * No service here actually imports configuration from Config Server (spring.config.import
 * isn't used anywhere in this project - each service reads its own local application.yml), so
 * Spring Cloud Config never auto-registers a health check for it the way it would for a real
 * config client. This makes "is Config Server reachable" an explicit check instead, since it's
 * still infrastructure every service nominally depends on.
 *
 * Bean name (minus the "HealthIndicator" suffix) becomes the component key in the /health
 * response, so this must stay named exactly configServerHealthIndicator to surface as
 * "configServer".
 */
public class ConfigServerHealthIndicator implements HealthIndicator {

    private final RestClient restClient;
    private final String healthUrl;

    public ConfigServerHealthIndicator(String baseUrl, String username, String password) {
        this.healthUrl = baseUrl + "/actuator/health";
        // Short, explicit timeouts: an unreachable Config Server must not make every /health
        // call on this service hang for the OS-default TCP timeout.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .build();
    }

    @Override
    public Health health() {
        try {
            String body = restClient.get()
                    .uri(healthUrl)
                    .retrieve()
                    .body(String.class);
            return Health.up()
                    .withDetail("url", healthUrl)
                    .withDetail("response", body)
                    .build();
        } catch (RestClientException ex) {
            return Health.down()
                    .withDetail("url", healthUrl)
                    .withException(ex)
                    .build();
        }
    }
}
