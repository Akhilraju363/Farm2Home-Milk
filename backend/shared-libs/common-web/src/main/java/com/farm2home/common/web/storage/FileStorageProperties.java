package com.farm2home.common.web.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.upload")
public class FileStorageProperties {

    /** Root directory uploaded files are written under - each category gets its own
     *  subdirectory, e.g. {baseDir}/customers/, {baseDir}/farms/, {baseDir}/products/. */
    private String baseDir = "uploads";

    private long maxFileSizeBytes = 5L * 1024 * 1024;

    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
}
