package com.farm2home.common.web.storage;

import com.farm2home.common.web.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    private FileStorageProperties propertiesWithBaseDir(Path dir) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBaseDir(dir.toString());
        return properties;
    }

    @Nested
    @DisplayName("store()")
    class Store {

        @Test
        @DisplayName("valid image → written under {baseDir}/{category}/{uuid}.{ext}, returns the relative path")
        void validFile_storedAndRelativePathReturned(@TempDir Path tempDir) throws IOException {
            FileStorageService service = new FileStorageService(propertiesWithBaseDir(tempDir));
            byte[] content = "fake-image-bytes".getBytes();
            MockMultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", content);

            String relativePath = service.store(file, "customers");

            assertThat(relativePath).startsWith("customers/").endsWith(".png");
            Path stored = tempDir.resolve(relativePath);
            assertThat(Files.exists(stored)).isTrue();
            assertThat(Files.readAllBytes(stored)).isEqualTo(content);
        }

        @Test
        @DisplayName("filename with no extension → stored with no extension, not a parsing error")
        void noExtension_storedWithoutOne(@TempDir Path tempDir) {
            FileStorageService service = new FileStorageService(propertiesWithBaseDir(tempDir));
            MockMultipartFile file = new MockMultipartFile("file", "noext", "image/png", "bytes".getBytes());

            String relativePath = service.store(file, "farms");

            assertThat(relativePath).doesNotContain(".");
        }

        @Test
        @DisplayName("null file → rejected before any filesystem write")
        void nullFile_rejected(@TempDir Path tempDir) {
            FileStorageService service = new FileStorageService(propertiesWithBaseDir(tempDir));

            assertThatThrownBy(() -> service.store(null, "customers"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("empty file → rejected")
        void emptyFile_rejected(@TempDir Path tempDir) {
            FileStorageService service = new FileStorageService(propertiesWithBaseDir(tempDir));
            MockMultipartFile empty = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> service.store(empty, "customers"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("file larger than the configured maximum → rejected")
        void oversized_rejected(@TempDir Path tempDir) {
            FileStorageProperties properties = propertiesWithBaseDir(tempDir);
            properties.setMaxFileSizeBytes(10);
            FileStorageService service = new FileStorageService(properties);
            MockMultipartFile tooLarge = new MockMultipartFile("file", "photo.png", "image/png", new byte[20]);

            assertThatThrownBy(() -> service.store(tooLarge, "customers"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("exceeds the maximum allowed size");
        }

        @Test
        @DisplayName("content type not in the allow-list → rejected")
        void disallowedContentType_rejected(@TempDir Path tempDir) {
            FileStorageProperties properties = propertiesWithBaseDir(tempDir);
            properties.setAllowedContentTypes(List.of("image/png"));
            FileStorageService service = new FileStorageService(properties);
            MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());

            assertThatThrownBy(() -> service.store(pdf, "customers"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported file type");
        }
    }
}
