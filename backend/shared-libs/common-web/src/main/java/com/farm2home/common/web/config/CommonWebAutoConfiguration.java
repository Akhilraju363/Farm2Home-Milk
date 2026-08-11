package com.farm2home.common.web.config;

import com.farm2home.common.web.client.RequestHeaderForwarder;
import com.farm2home.common.web.exception.GlobalExceptionHandler;
import com.farm2home.common.web.storage.FileStorageProperties;
import com.farm2home.common.web.storage.FileStorageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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

    /** Used by BFF-style aggregator/proxy services (dashboard-service, reports-service, ...) to
     *  forward the caller's identity + correlation id onto their own downstream calls. */
    @Bean
    public RequestHeaderForwarder requestHeaderForwarder() {
        return new RequestHeaderForwarder();
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