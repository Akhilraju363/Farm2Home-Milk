package com.farm2home.app.config;

import com.farm2home.app.security.InternalCallToken;
import com.farm2home.common.core.constants.HeaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Resolves {@code lb://<service-name>/...} calls in the single JVM.
 *
 * <p>Every embedded service ships a {@code config.WebClientConfig} declaring a
 * {@code @LoadBalanced WebClient.Builder loadBalancedWebClientBuilder} and {@code client.*}
 * components that do {@code builder.baseUrl("lb://order-service")...}. Those config classes are
 * not scanned, so this module supplies the one builder they inject by type.
 *
 * <p>In production those {@code lb://} URIs resolve through Eureka + Spring Cloud LoadBalancer.
 * Here there is no Eureka and every target controller lives in <b>this same process</b>, so the
 * builder carries a filter that simply rewrites {@code lb://<anything>/<path>} to
 * {@code http://localhost:<this app's port>/<path>} — a genuine loopback HTTP call back into
 * this JVM's own dispatcher (so the JWT filter, {@code @PreAuthorize}, etc. all still apply to
 * the inter-service hop). The caller's bearer token rides along via
 * {@code RequestHeaderForwarder} (see common-web), which is what authenticates the loopback.
 *
 * <p>This deliberately avoids re-enabling {@code spring-cloud-loadbalancer} +
 * {@code SimpleDiscoveryClient} for the aggregate: that stack pulls ~25 startup warnings and,
 * with static instances, mis-reconstructs the {@code lb://} scheme on this Spring Cloud
 * version. A one-line in-process rewrite is both simpler and exactly right for one JVM.
 *
 * <p><b>Not-yet-embedded targets</b> ({@code lb://delivery-service},
 * {@code lb://order-service} — Groups 3–4): the rewrite still points at localhost, the missing
 * controller returns 404, and the calling {@code client.*} code already treats a failed
 * cross-service call as "unknown" (it catches {@code WebClientException}). So those features
 * degrade gracefully rather than erroring — documented in the Group 2 report.
 */
@Configuration
public class LoadBalancerClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerClientConfig.class);

    /** Captures this app's actual HTTP port once the web server is up (handles {@code PORT},
     *  {@code server.port=0} in tests, etc.). */
    static final class SelfPortHolder implements ApplicationListener<WebServerInitializedEvent> {
        private volatile int port = 8080;

        @Override
        public void onApplicationEvent(WebServerInitializedEvent event) {
            this.port = event.getWebServer().getPort();
            log.info("In-process lb:// loopback resolves to http://localhost:{}", this.port);
        }
    }

    @Bean
    SelfPortHolder selfPortHolder() {
        return new SelfPortHolder();
    }

    @Bean
    ExchangeFilterFunction inProcessLoopbackFilter(SelfPortHolder selfPort, InternalCallToken internalCallToken) {
        return (request, next) -> {
            URI url = request.url();
            if ("lb".equalsIgnoreCase(url.getScheme())) {
                URI target = UriComponentsBuilder.fromUri(url)
                        .scheme("http")
                        .host("localhost")
                        .port(selfPort.port)
                        .build(true)
                        .toUri();
                // Stamp the per-process proof-of-origin secret. SpikeJwtAuthenticationFilter
                // trusts an inbound X-User-* system identity (invoice/notification clients send a
                // fixed one, not a bearer) only when this secret is also present — a forged
                // X-User-* from any external client cannot authenticate. Bearer-carrying loopback
                // calls (RequestHeaderForwarder) are unaffected: the filter checks the bearer first.
                return next.exchange(ClientRequest.from(request)
                        .url(target)
                        .headers(h -> h.set(HeaderConstants.X_INTERNAL_AUTH, internalCallToken.value()))
                        .build());
            }
            return next.exchange(request);
        };
    }

    /**
     * The single {@code loadBalancedWebClientBuilder} (bean name kept identical to the 9
     * excluded {@code WebClientConfig} copies). Not {@code @LoadBalanced} — the loopback filter
     * above replaces that machinery.
     */
    @Bean
    WebClient.Builder loadBalancedWebClientBuilder(ExchangeFilterFunction inProcessLoopbackFilter) {
        return WebClient.builder().filter(inProcessLoopbackFilter);
    }
}
