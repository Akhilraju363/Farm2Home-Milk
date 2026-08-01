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
     *  "customers/{uuid}.jpg" becomes reachable at GET /uploads/customers/{uuid}.jpg. */
    @Bean
    public WebMvcConfigurer uploadResourceHandlerConfigurer(FileStorageProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String location = java.nio.file.Path.of(properties.getBaseDir())
                        .toAbsolutePath().toUri().toString();
                registry.addResourceHandler("/uploads/**").addResourceLocations(location);
            }
        };
    }
}