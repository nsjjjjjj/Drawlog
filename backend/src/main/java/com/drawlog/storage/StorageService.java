package com.drawlog.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    StoredFile storeImage(MultipartFile file);
    void deleteImage(String imageUrl);
}
