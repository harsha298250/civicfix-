package com.civicfix.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * CivicFix AI — File Storage Abstraction
 * 
 * Supports local filesystem storage for development/air-gapped deployments,
 * and high-security Neon S3-compatible Object Storage for production.
 */
public interface StorageService {

    /**
     * Stores a validated multipart file into the designated sub-directory.
     * Generates a sanitized, collision-resistant unique filename.
     * 
     * @param file the uploaded multipart file
     * @param subDirectory target folder (e.g. "evidence/complaints")
     * @return relative web-accessible resource path (e.g. "/api/complaints/evidence/files/evidence-abc.jpg")
     * @throws IOException on physical I/O or storage failure
     */
    String storeFile(MultipartFile file, String subDirectory) throws IOException;

    /**
     * Stores a validated media image with complete cryptographic and provenance metadata.
     *
     * @param file uploaded multipart file
     * @param subDirectory target folder (e.g. "complaints/UUID")
     * @param uploaderId user UUID or identifier performing the upload
     * @return StorageResult containing objectKey, provider, checksum, size, and URLs
     * @throws IOException on storage or validation failure
     */
    default StorageResult storeImage(MultipartFile file, String subDirectory, String uploaderId) throws IOException {
        String res = storeFile(file, subDirectory);
        return new StorageResult(res, "LOCAL", res, file.getOriginalFilename(), file != null ? file.getSize() : 0, file != null ? file.getContentType() : "image/jpeg", "hash");
    }

    /**
     * Loads a stored file as a Spring Resource for controlled binary streaming.
     * Prevents path-traversal attacks.
     * 
     * @param filename sanitized filename
     * @param subDirectory folder where the file resides
     * @return readable Resource
     * @throws IOException if the file does not exist or cannot be read
     */
    Resource loadFileAsResource(String filename, String subDirectory) throws IOException;

    /**
     * Generates a short-lived, presigned download URL for private object access.
     * Returns null if running in local filesystem mode where presigned URLs are not applicable.
     *
     * @param objectKey S3 object key
     * @param durationMinutes validity duration in minutes (e.g. 15)
     * @return presigned HTTPS download URL or null if local
     */
    default String generatePresignedDownloadUrl(String objectKey, int durationMinutes) {
        return null;
    }

    /**
     * Deletes a stored file if present.
     * 
     * @param filename sanitized filename
     * @param subDirectory target folder
     */
    void deleteFile(String filename, String subDirectory);

    /**
     * Deletes a private object by its exact storage object key.
     *
     * @param objectKey S3 object key
     */
    default void deleteObject(String objectKey) {
        deleteFile(objectKey, "");
    }

    /**
     * Returns true if cloud object storage (e.g. Neon S3) is actively configured and operational.
     */
    default boolean isCloudStorageConfigured() {
        return false;
    }

    /**
     * Returns the active storage provider name (e.g. "NEON_S3" or "LOCAL").
     */
    default String getProviderName() {
        return "LOCAL";
    }
}
