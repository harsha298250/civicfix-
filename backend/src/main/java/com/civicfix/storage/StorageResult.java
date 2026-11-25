package com.civicfix.storage;

/**
 * StorageResult encapsulates the metadata of an uploaded and verified image.
 */
public record StorageResult(
        String objectKey,
        String storageProvider,
        String resourceUrl,
        String originalFilename,
        long fileSize,
        String contentType,
        String sha256Checksum
) {}
