package com.farm2home.common.web.config;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.exception.GlobalExceptionHandler;
import com.farm2home.common.web.security.GatewayTrust;
import com.farm2home.common.web.security.GatewayTrustHeaderFilter;
import com.farm2home.common.web.storage.FileStorageProperties;
import com.farm2home.common.web.storage.FileStorageService;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties.class)
public class CommonWebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public FileStorageService fileStorageService(FileStorageProperties properties) {
        return new FileStorageService(properties);
    }

    /** Spring Boot's own servlet-container multipart limit defaults to 1MB (both max-file-size
     *  and max-request-size) unless something overrides it - no service in this codebase ever
     *  did, so every upload endpoint (product images, customer profile photos, farm images) has
     *  been silently rejecting anything over ~1MB with an opaque 500 "An unexpected error
     *  occurred" (MaxUploadSizeExceededException has no @ResponseStatus, so it fell into
     *  GlobalExceptionHandler's generic catch-all) - even though FileStorageService.validate()
     *  already implements a proper, cleanly-messaged 5MB check via FileStorageProperties, and
     *  every upload UI in the frontend advertises "Max 5 MB". The container limit here is set
     *  above that 5MB application-level limit (not equal to it) so a legitimate ≤5MB file is
     *  never rejected by multipart framing/boundary overhead before FileStorageService ever sees
     *  it - that check, not this one, is meant to be the actual gate a user hits. */
    @Bean
    public MultipartConfigElement multipartConfigElement(FileStorageProperties properties) {
        long limitBytes = properties.getMaxFileSizeBytes() + (1024 * 1024);
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(limitBytes));
        factory.setMaxRequestSize(DataSize.ofBytes(limitBytes));
        return factory.createMultipartConfig();
    }

    /**
     * Verifies that a request carrying {@code X-User-*} identity headers actually came through
     * the api-gateway (which sets {@code X-Internal-Auth} to this shared secret). Blank in
     * local/dev/test - enforcement is then disabled and behaviour is unchanged.
     *
     * <p>Env var: {@code FARM2HOME_GATEWAY_INTERNAL_SECRET} (relaxed-bound from
     * {@code farm2home.gateway.internal-secret}).
     */
    @Bean
    public GatewayTrust gatewayTrust(
            @Value("${farm2home.gateway.internal-secret:}") String internalSecret) {
        return new GatewayTrust(internalSecret);
    }

    /**
     * Strips forged {@code X-User-*}/{@code X-Internal-Auth} headers from any request that did
     * not arrive through the api-gateway (once a secret is configured). Runs before Spring
     * Security. No-op when enforcement is disabled.
     */
    @Bean
    public GatewayTrustHeaderFilter gatewayTrustHeaderFilter(GatewayTrust gatewayTrust) {
        return new GatewayTrustHeaderFilter(gatewayTrust);
    }

    /** Used by BFF-style aggregator/proxy services (dashboard-service, reports-service, ...) to
     *  forward the caller's identity + correlation id onto their own downstream calls. Also
     *  attaches the gateway trust secret so the downstream service accepts the forwarded
     *  identity (the aggregator is itself a trusted, gateway-authenticated hop). */
    @Bean
    public RequestHeaderForwarder requestHeaderForwarder(GatewayTrust gatewayTrust) {
        return new RequestHeaderForwarder(gatewayTrust);
    }

    /** Serves everything under app.upload.base-dir at /uploads/** - e.g. a file stored as
     *  "customers/{uuid}.jpg" becomes reachable at GET /uploads/customers/{uuid}.jpg.
     *
     *  The base directory is created eagerly, before the resource location is registered, rather
     *  than relying on FileStorageService#store() to lazily create it on first upload. Spring's
     *  static resource handler resolves/validates its location at context-startup time - if the
     *  directory doesn't exist yet then (true for every service the first time anyone uploads a
     *  file to a fresh checkout/deployment), later requests for files that genuinely exist 500
     *  instead of 200 until the service is restarted. Reproduced live against farm-service: an
     *  upload succeeded and returned a valid imageUrl, but GET-ing that same URL 500'd until a
     *  restart, because backend/farm-service/uploads/ didn't exist when the app started. */
    @Bean
    public WebMvcConfigurer uploadResourceHandlerConfigurer(FileStorageProperties properties) {
        java.nio.file.Path baseDir = java.nio.file.Path.of(properties.getBaseDir());
        try {
            java.nio.file.Files.createDirectories(baseDir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to create upload base directory: " + baseDir, e);
        }
        String location = baseDir.toAbsolutePath().toUri().toString();
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/uploads/**").addResourceLocations(location);
            }
        };
    }
}