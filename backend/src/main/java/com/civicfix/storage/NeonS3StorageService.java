package com.civicfix.storage;

import com.civicfix.common.exception.BadRequestException;
import com.civicfix.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * NeonS3StorageService
 *
 * Implements private S3-compatible complaint photograph storage for Neon Object Storage.
 *
 * Security & Reliability Specifications:
 * 1. Configured via environment variables:
 *    - AWS_ENDPOINT_URL_S3
 *    - AWS_ACCESS_KEY_ID
 *    - AWS_SECRET_ACCESS_KEY
 *    - AWS_REGION (default: us-east-2)
 *    - NEON_STORAGE_BUCKET (default: civicfix-complaint-images)
 * 2. Strict Magic Byte Verification (JPEG, PNG, WebP) with declared MIME mismatch rejection.
 * 3. 10MB per-image size enforcement.
 * 4. Unguessable collision-resistant UUID object keys (original filenames are never used as keys).
 * 5. Private bucket enforcement: no public ACLs, download access exclusively via short-lived presigned URLs.
 * 6. Never logs access keys or secret keys.
 * 7. Graceful fallback to LocalStorageService when running in air-gapped/local test mode.
 */
@Service
@Primary
public class NeonS3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(NeonS3StorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    @Value("${civicfix.storage.endpoint:${AWS_ENDPOINT_URL_S3:}}")
    private String endpointUrl;

    @Value("${civicfix.storage.access-key:${AWS_ACCESS_KEY_ID:}}")
    private String accessKeyId;

    @Value("${civicfix.storage.secret-key:${AWS_SECRET_ACCESS_KEY:}}")
    private String secretAccessKey;

    @Value("${civicfix.storage.region:${AWS_REGION:us-east-2}}")
    private String region = "us-east-2";

    @Value("${civicfix.storage.bucket:${NEON_STORAGE_BUCKET:civicfix-complaint-images}}")
    private String bucketName = "civicfix-complaint-images";

    @Value("${civicfix.storage.provider:${STORAGE_PROVIDER:local}}")
    private String configuredProvider;

    private final LocalStorageService localStorageService;

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private boolean initialized = false;

    @Autowired
    public NeonS3StorageService(LocalStorageService localStorageService) {
        this.localStorageService = localStorageService;
    }

    /**
     * Testing constructor allowing dependency injection of mock/custom S3 clients.
     */
    public NeonS3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            String bucketName,
            LocalStorageService localStorageService
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
        this.localStorageService = localStorageService;
        this.initialized = true;
    }

    public NeonS3StorageService(
            String bucketName,
            String region,
            String endpointUrl,
            String accessKeyId,
            String secretAccessKey,
            LocalStorageService localStorageService,
            S3Client s3Client,
            S3Presigner s3Presigner
    ) {
        this.bucketName = bucketName;
        this.region = region;
        this.endpointUrl = endpointUrl;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.localStorageService = localStorageService;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.initialized = true;
    }

    public StorageResult storeImage(MultipartFile file, UUID complaintId) throws IOException {
        return storeImage(file, "complaints/" + complaintId, null);
    }

    private boolean isNeonProviderConfigured() {
        return "neon".equalsIgnoreCase(configuredProvider) || "s3".equalsIgnoreCase(configuredProvider);
    }

    @jakarta.annotation.PostConstruct
    public void validateConfigurationOnStartup() {
        if (isNeonProviderConfigured()) {
            initS3Client();
        }
    }

    private synchronized void initS3Client() {
        if (this.initialized) return;

        if (isNeonProviderConfigured()) {
            List<String> missing = new ArrayList<>();
            if (accessKeyId == null || accessKeyId.isBlank()) missing.add("AWS_ACCESS_KEY_ID");
            if (secretAccessKey == null || secretAccessKey.isBlank()) missing.add("AWS_SECRET_ACCESS_KEY");
            if (bucketName == null || bucketName.isBlank()) missing.add("NEON_STORAGE_BUCKET");
            if (endpointUrl == null || endpointUrl.isBlank()) missing.add("AWS_ENDPOINT_URL_S3");

            if (!missing.isEmpty()) {
                log.error("[NEON_STORAGE] STORAGE_PROVIDER='{}' requires configuration: {}", configuredProvider, missing);
                throw new IllegalStateException("Neon Object Storage provider configured ('" + configuredProvider +
                        "') but required configuration is missing: " + String.join(", ", missing) +
                        ". Strict production startup validation failed.");
            }
        }

        if (accessKeyId != null && !accessKeyId.isBlank() &&
            secretAccessKey != null && !secretAccessKey.isBlank()) {
            try {
                Region awsRegion = Region.of(region != null && !region.isBlank() ? region.trim() : "us-east-2");
                StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())
                );

                S3ClientBuilder clientBuilder = S3Client.builder()
                        .region(awsRegion)
                        .credentialsProvider(creds);

                S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                        .region(awsRegion)
                        .credentialsProvider(creds);

                if (endpointUrl != null && !endpointUrl.isBlank()) {
                    URI endpointUri = URI.create(endpointUrl.trim());
                    clientBuilder.endpointOverride(endpointUri).forcePathStyle(true);
                    presignerBuilder.endpointOverride(endpointUri);
                }

                this.s3Client = clientBuilder.build();
                this.s3Presigner = presignerBuilder.build();
                this.initialized = true;
                log.info("[NEON_STORAGE] Initialized S3 client for bucket '{}' in region '{}'", bucketName, region);
            } catch (Exception ex) {
                if (isNeonProviderConfigured()) {
                    log.error("[NEON_STORAGE] Failed to initialize required S3 client in production mode: {}", ex.getMessage(), ex);
                    throw new IllegalStateException("Failed to initialize required Neon S3 storage client: " + ex.getMessage(), ex);
                }
                log.warn("[NEON_STORAGE] Failed to initialize S3 client: {}. Falling back to local storage.", ex.getMessage());
            }
        } else {
            log.info("[NEON_STORAGE] AWS_ACCESS_KEY_ID not configured; running in local storage fallback mode.");
        }
    }

    private S3Client getS3Client() {
        if (!this.initialized) {
            initS3Client();
        }
        return this.s3Client;
    }

    private S3Presigner getS3Presigner() {
        if (!this.initialized) {
            initS3Client();
        }
        return this.s3Presigner;
    }

    @Override
    public boolean isCloudStorageConfigured() {
        return getS3Client() != null;
    }

    @Override
    public String getProviderName() {
        return isCloudStorageConfigured() ? "NEON_S3" : "LOCAL";
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        StorageResult result = storeImage(file, subDirectory, null);
        return result.resourceUrl();
    }

    @Override
    public StorageResult storeImage(MultipartFile file, String subDirectory, String uploaderId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded evidence photo cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException(String.format("File '%s' exceeds the 10MB maximum upload limit.", file.getOriginalFilename()));
        }

        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new BadRequestException("Uploaded file contains zero bytes.");
        }

        String rawContentType = file.getContentType();
        if (rawContentType == null || !ALLOWED_MIME_TYPES.contains(rawContentType.toLowerCase().trim())) {
            throw new BadRequestException("Invalid file type: '" + rawContentType + "'. Only JPG, PNG, and WebP images are permitted.");
        }
        String declaredType = rawContentType.toLowerCase().trim();

        // 1. Strict Magic Bytes Inspection
        String detectedFormat = inspectMagicBytes(bytes);
        if (detectedFormat == null) {
            throw new BadRequestException("Invalid file format. Uploaded file signature does not match permitted image formats (JPG, PNG, WebP).");
        }

        // 2. Mismatch Rejection
        validateMimeAgreement(declaredType, detectedFormat);

        // 3. Path Traversal & Filename Sanitization
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo." + detectedFormat);
        if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
            throw new BadRequestException("Invalid path sequence in filename.");
        }

        // 4. Generate Unguessable Collision-Resistant Unique Object Key
        String cleanSubDir = (subDirectory != null ? subDirectory.trim() : "complaints")
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (cleanSubDir.contains("..")) {
            throw new BadRequestException("Invalid sub-directory path sequence.");
        }

        String uniqueFilename = UUID.randomUUID().toString() + "." + detectedFormat;
        String objectKey = cleanSubDir + "/" + uniqueFilename;

        // 5. Compute SHA-256 Checksum
        String sha256 = calculateSha256(bytes);

        // 6. Upload to S3 if configured; otherwise use LocalStorageService
        if (isCloudStorageConfigured()) {
            try {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("original-filename", originalFilename);
                metadata.put("sha256-checksum", sha256);
                if (uploaderId != null && !uploaderId.isBlank()) {
                    metadata.put("uploader-id", uploaderId);
                }

                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(toCanonicalMimeType(detectedFormat))
                        .metadata(metadata)
                        .build();

                getS3Client().putObject(putRequest, RequestBody.fromBytes(bytes));
                log.info("[NEON_STORAGE] Successfully uploaded image object '{}' ({} bytes, sha256: {})", objectKey, bytes.length, sha256);

                String resourceUrl = "/api/complaints/photos/files/" + uniqueFilename;
                return new StorageResult(
                        objectKey,
                        "NEON_S3",
                        resourceUrl,
                        originalFilename,
                        bytes.length,
                        toCanonicalMimeType(detectedFormat),
                        sha256
                );
            } catch (Exception ex) {
                log.error("[NEON_STORAGE] S3 upload failed for key '{}': {}", objectKey, ex.getMessage());
                throw new IOException("Failed to persist image to cloud storage: " + ex.getMessage(), ex);
            }
        } else {
            if (isNeonProviderConfigured()) {
                throw new IllegalStateException("[NEON_STORAGE] STORAGE_PROVIDER is '" + configuredProvider + "' but S3 client is uninitialized. Refusing silent fallback to local storage in production.");
            }
            // Local fallback
            if (localStorageService != null) {
                return localStorageService.storeImage(file, subDirectory, uploaderId);
            }
            throw new IOException("No storage provider is currently configured.");
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String objectKey, int durationMinutes) {
        if (!isCloudStorageConfigured() || objectKey == null || objectKey.isBlank()) {
            return null;
        }

        try {
            int duration = durationMinutes > 0 ? durationMinutes : 15;
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey.trim())
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(duration))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presigned = getS3Presigner().presignGetObject(presignRequest);
            return presigned.url().toString();
        } catch (Exception ex) {
            log.error("[NEON_STORAGE] Failed to generate presigned download URL for key '{}': {}", objectKey, ex.getMessage());
            return null;
        }
    }

    @Override
    public Resource loadFileAsResource(String filename, String subDirectory) throws IOException {
        String cleanName = StringUtils.cleanPath(filename);
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")) {
            throw new BadRequestException("Invalid filename path sequence.");
        }

        String objectKey = (subDirectory != null && !subDirectory.isBlank() ? subDirectory + "/" : "") + cleanName;

        if (isCloudStorageConfigured()) {
            try {
                GetObjectRequest getRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build();

                ResponseInputStream<GetObjectResponse> s3Stream = getS3Client().getObject(getRequest);
                byte[] data = s3Stream.readAllBytes();
                return new ByteArrayResource(data, cleanName);
            } catch (NoSuchKeyException e) {
                throw new ResourceNotFoundException("File not found in storage: " + cleanName);
            } catch (Exception e) {
                throw new IOException("Failed to retrieve file from storage: " + e.getMessage(), e);
            }
        } else if (localStorageService != null) {
            return localStorageService.loadFileAsResource(filename, subDirectory);
        } else {
            throw new ResourceNotFoundException("File not found: " + cleanName);
        }
    }

    @Override
    public void deleteFile(String filename, String subDirectory) {
        String cleanSubDir = (subDirectory != null ? subDirectory.trim() : "")
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        String objectKey = cleanSubDir.isEmpty() ? filename : cleanSubDir + "/" + filename;
        deleteObject(objectKey);
    }

    @Override
    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;

        if (isCloudStorageConfigured()) {
            try {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey.trim())
                        .build();
                getS3Client().deleteObject(deleteRequest);
                log.info("[NEON_STORAGE] Deleted object '{}' from bucket '{}'", objectKey, bucketName);
            } catch (Exception ex) {
                log.warn("[NEON_STORAGE] Failed to delete object '{}' from S3: {}", objectKey, ex.getMessage());
            }
        } else if (localStorageService != null) {
            localStorageService.deleteObject(objectKey);
        }
    }

    public static String inspectMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 &&
            bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }

        // WebP: RIFF....WEBP
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
            bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }

        return null;
    }

    private void validateMimeAgreement(String declaredMime, String detectedFormat) {
        switch (detectedFormat) {
            case "jpg":
                if (!declaredMime.equals("image/jpeg") && !declaredMime.equals("image/jpg")) {
                    throw new BadRequestException("Declared content type '" + declaredMime + "' does not match binary JPEG content.");
                }
                break;
            case "png":
                if (!declaredMime.equals("image/png")) {
                    throw new BadRequestException("Declared content type '" + declaredMime + "' does not match binary PNG content.");
                }
                break;
            case "webp":
                if (!declaredMime.equals("image/webp")) {
                    throw new BadRequestException("Declared content type '" + declaredMime + "' does not match binary WebP content.");
                }
                break;
            default:
                throw new BadRequestException("Unsupported image format: " + detectedFormat);
        }
    }

    private String toCanonicalMimeType(String format) {
        return switch (format) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "";
        }
    }
}
