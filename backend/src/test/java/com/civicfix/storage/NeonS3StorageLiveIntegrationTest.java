package com.civicfix.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Controlled Live Integration Test for Neon S3 Object Storage (Conditional)")
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
class NeonS3StorageLiveIntegrationTest {

    @Test
    @DisplayName("Verify end-to-end lifecycle on real Neon S3 Object Storage bucket")
    void testRealNeonS3Lifecycle() throws Exception {
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        String regionStr = System.getenv().getOrDefault("AWS_REGION", "us-east-2");
        String bucket = System.getenv().getOrDefault("NEON_STORAGE_BUCKET", "civicfix-complaint-images");
        String endpoint = System.getenv().getOrDefault("AWS_ENDPOINT_URL_S3", "");

        assertNotNull(accessKey, "AWS_ACCESS_KEY_ID is required for live test");
        assertNotNull(secretKey, "AWS_SECRET_ACCESS_KEY is required for live test");

        Region region = Region.of(regionStr);
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
        );

        S3Client s3Client;
        S3Presigner presigner;

        if (endpoint != null && !endpoint.isBlank()) {
            s3Client = S3Client.builder()
                    .region(region)
                    .credentialsProvider(creds)
                    .endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();

            presigner = S3Presigner.builder()
                    .region(region)
                    .credentialsProvider(creds)
                    .endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    .build();
        } else {
            s3Client = S3Client.builder()
                    .region(region)
                    .credentialsProvider(creds)
                    .build();

            presigner = S3Presigner.builder()
                    .region(region)
                    .credentialsProvider(creds)
                    .build();
        }

        NeonS3StorageService liveStorage = new NeonS3StorageService(
                bucket,
                regionStr,
                endpoint,
                accessKey,
                secretKey,
                new LocalStorageService("./tmp-uploads"),
                s3Client,
                presigner
        );

        assertTrue(liveStorage.isCloudStorageConfigured());

        // 1. Upload test image
        UUID complaintId = UUID.randomUUID();
        byte[] testImageBytes = TestImageHelper.createValidJpeg(4, 4);
        MockMultipartFile testFile = new MockMultipartFile(
                "files",
                "live_test.jpg",
                "image/jpeg",
                testImageBytes
        );

        StorageResult result = liveStorage.storeImage(testFile, complaintId);
        assertNotNull(result);
        assertEquals("NEON_S3", result.storageProvider());
        String objectKey = result.objectKey();

        try {
            // 2. Verify object exists in bucket via HeadObject
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            assertEquals(testImageBytes.length, head.contentLength());

            // 3. Generate presigned URL
            String presignedUrl = liveStorage.generatePresignedDownloadUrl(objectKey, 15);
            assertNotNull(presignedUrl);
            assertTrue(presignedUrl.startsWith("http"));
        } finally {
            // 4. Clean up test object
            liveStorage.deleteObject(objectKey);
        }
    }
}
