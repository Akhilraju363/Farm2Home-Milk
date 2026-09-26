package com.farm2home.common.web.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Trivial @ConfigurationProperties bean, but its defaults are real behavior (they apply
 *  whenever app.upload.* is left unset in a service's own application.yml) - worth pinning down
 *  so an accidental change to one isn't silent. */
class FileStoragePropertiesTest {

    @Test
    void defaults() {
        FileStorageProperties properties = new FileStorageProperties();

        assertThat(properties.getBaseDir()).isEqualTo("uploads");
        assertThat(properties.getMaxFileSizeBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(properties.getAllowedContentTypes()).containsExactly("image/jpeg", "image/png", "image/webp");
    }

    @Test
    void settersOverrideDefaults() {
        FileStorageProperties properties = new FileStorageProperties();

        properties.setBaseDir("/data/uploads");
        properties.setMaxFileSizeBytes(1024);
        properties.setAllowedContentTypes(java.util.List.of("application/pdf"));

        assertThat(properties.getBaseDir()).isEqualTo("/data/uploads");
        assertThat(properties.getMaxFileSizeBytes()).isEqualTo(1024);
        assertThat(properties.getAllowedContentTypes()).containsExactly("application/pdf");
    }
}
