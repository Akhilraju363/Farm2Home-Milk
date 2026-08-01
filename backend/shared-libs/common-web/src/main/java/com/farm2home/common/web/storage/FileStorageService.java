package com.farm2home.common.web.storage;

import com.farm2home.common.web.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores uploaded images on the local filesystem under {@code app.upload.base-dir}, one
 * subdirectory per category (customers/farms/products/...). Returns a relative path
 * ("{category}/{uuid}.{ext}") - not an absolute filesystem path - for the caller to persist
 * and later resolve into a servable URL. Registered as a bean by CommonWebAutoConfiguration,
 * not component-scanned (consuming services never scan com.farm2home.common.web).
 */
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileStorageProperties properties;

    public String store(MultipartFile file, String category) {
        validate(file);

        try {
            Path categoryDir = Path.of(properties.getBaseDir(), category);
            Files.createDirectories(categoryDir);

            String extension = extensionOf(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path target = categoryDir.resolve(filename);

            file.transferTo(target);
            log.info("Stored uploaded file: {}", target);

            return category + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BadRequestException(
                    "File exceeds the maximum allowed size of " + properties.getMaxFileSizeBytes() + " bytes.");
        }
        if (!properties.getAllowedContentTypes().contains(file.getContentType())) {
            throw new BadRequestException(
                    "Unsupported file type '" + file.getContentType() + "'. Allowed: "
                            + properties.getAllowedContentTypes());
        }
    }

    private String extensionOf(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }
}
