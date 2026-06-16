package com.drawlog.storage;

public record StoredFile(String imageUrl, String originalFilename, String contentType, long sizeBytes) {
}
