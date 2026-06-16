package com.drawlog.storage;

import com.drawlog.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalStorageService implements StorageService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/webp");
    private final AppProperties properties;

    public LocalStorageService(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile storeImage(MultipartFile file) {
        validate(file);
        String extension = extensionFor(file.getContentType());
        String filename = UUID.randomUUID() + "." + extension;
        Path uploadDir = Path.of(properties.getUploadDir());
        Path destination = uploadDir.resolve(filename).normalize();
        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.", e);
        }
        return new StoredFile(properties.getPublicUploadPath() + "/" + filename, file.getOriginalFilename(), file.getContentType(), file.getSize());
    }

    @Override
    public void deleteImage(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(properties.getPublicUploadPath() + "/")) {
            return;
        }
        String filename = imageUrl.substring((properties.getPublicUploadPath() + "/").length());
        Path uploadDir = Path.of(properties.getUploadDir()).normalize();
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.startsWith(uploadDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // File cleanup should not block database cleanup.
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일이 필요합니다.");
        }
        if (file.getSize() > properties.getMaxImageBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 파일이 너무 큽니다.");
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG 또는 WebP 이미지만 업로드할 수 있습니다.");
        }
        String original = file.getOriginalFilename();
        String lower = original == null ? "" : original.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".webp"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 확장자는 .png 또는 .webp 이어야 합니다.");
        }
    }

    private String extensionFor(String contentType) {
        return "image/webp".equals(contentType) ? "webp" : "png";
    }
}
