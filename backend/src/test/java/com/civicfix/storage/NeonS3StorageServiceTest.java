package com.civicfix.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NeonS3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private LocalStorageService localStorageService;

    private NeonS3StorageService storageService;

    private static final String BUCKET_NAME = "civicfix-complaint-images";
    private static final String REGION = "us-east-2";
    private static final String ENDPOINT = "https://s3.us-east-2.amazonaws.com";

    @BeforeEach
    void setUp() {
        storageService = new NeonS3StorageService(
                BUCKET_NAME,
                REGION,
                ENDPOINT,
                "AKIA_TEST_KEY",
                "SECRET_TEST_KEY",
                localStorageService,
                s3Client,
                s3Presigner
        );
    }

    @Test
    @DisplayName("Upload valid JPEG to Neon S3: calls putObject with collision-resistant key and persists metadata")
    void storeImage_ValidJpeg_Success() throws Exception {
        byte[] jpegBytes = TestImageHelper.createValidJpeg(20, 20);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "incident_road.jpg",
                "image/jpeg",
                jpegBytes
        );

        UUID complaintId = UUID.randomUUID();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageResult result = storageService.storeImage(file, complaintId);

        assertNotNull(result);
        assertEquals("NEON_S3", result.storageProvider());
        assertTrue(result.objectKey().startsWith("complaints/" + complaintId + "/"));
        assertTrue(result.objectKey().endsWith(".jpg"));
        assertEquals("image/jpeg", result.contentType());
        assertEquals(jpegBytes.length, result.fileSize());
        assertNotNull(result.sha256Checksum());
        assertEquals(64, result.sha256Checksum().length());

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest capturedReq = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedReq.bucket());
        assertEquals(result.objectKey(), capturedReq.key());
        assertEquals("image/jpeg", capturedReq.contentType());
        assertEquals("incident_road.jpg", capturedReq.metadata().get("original-filename"));
        assertNotNull(capturedReq.metadata().get("sha256-checksum"));
    }

    @Test
    @DisplayName("Upload valid PNG to Neon S3: stores with .png extension")
    void storeImage_ValidPng_Success() throws Exception {
        byte[] pngBytes = TestImageHelper.createValidPng(16, 16);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "pothole_leak.png",
                "image/png",
                pngBytes
        );

        UUID complaintId = UUID.randomUUID();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageResult result = storageService.storeImage(file, complaintId);

        assertNotNull(result);
        assertEquals("NEON_S3", result.storageProvider());
        assertTrue(result.objectKey().startsWith("complaints/" + complaintId + "/"));
        assertTrue(result.objectKey().endsWith(".png"));
        assertEquals("image/png", result.contentType());
    }

    @Test
    @DisplayName("Upload valid WebP to Neon S3: stores with .webp extension")
    void storeImage_ValidWebp_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "defect.webp",
                "image/webp",
                TestImageHelper.TINY_WEBP
        );

        UUID complaintId = UUID.randomUUID();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        StorageResult result = storageService.storeImage(file, complaintId);

        assertNotNull(result);
        assertEquals("NEON_S3", result.storageProvider());
        assertTrue(result.objectKey().endsWith(".webp"));
    }

    @Test
    @DisplayName("Magic bytes verification: rejects fake text file disguised as JPEG")
    void storeImage_DisguisedTextFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.jpg",
                "image/jpeg",
                "<?php echo 'exploit'; ?>".getBytes(StandardCharsets.UTF_8)
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> storageService.storeImage(file, UUID.randomUUID())
        );

        assertTrue(ex.getMessage().contains("file signature") || ex.getMessage().contains("permitted"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("MIME mismatch verification: rejects PNG file disguised with image/jpeg Content-Type")
    void storeImage_MimeMismatch_ThrowsException() {
        byte[] pngBytes = TestImageHelper.createValidPng(10, 10);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "spoofed.jpg",
                "image/jpeg", // Claimed JPEG, but binary is PNG
                pngBytes
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> storageService.storeImage(file, UUID.randomUUID())
        );

        assertTrue(ex.getMessage().contains("does not match binary PNG content"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("File size validation: rejects image exceeding 10MB limit")
    void storeImage_OversizedFile_ThrowsException() {
        byte[] fakeOversizedBytes = new byte[10 * 1024 * 1024 + 1];
        // Set JPEG magic bytes at header so signature check passes, size check fails
        fakeOversizedBytes[0] = (byte) 0xFF;
        fakeOversizedBytes[1] = (byte) 0xD8;
        fakeOversizedBytes[2] = (byte) 0xFF;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "huge_file.jpg",
                "image/jpeg",
                fakeOversizedBytes
        );

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> storageService.storeImage(file, UUID.randomUUID())
        );

        assertTrue(ex.getMessage().contains("exceeds the 10MB maximum upload limit"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("Generate presigned download URL: calls S3Presigner with 15-minute expiration")
    void generatePresignedDownloadUrl_Success() throws Exception {
        String objectKey = "complaints/test-id/photo1.jpg";
        URL mockUrl = new URL("https://civicfix-complaint-images.s3.us-east-2.amazonaws.com/" + objectKey + "?X-Amz-Signature=xyz123");

        PresignedGetObjectRequest presignedReq = mock(PresignedGetObjectRequest.class);
        when(presignedReq.url()).thenReturn(mockUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedReq);

        String signedUrl = storageService.generatePresignedDownloadUrl(objectKey, 15);

        assertNotNull(signedUrl);
        assertEquals(mockUrl.toString(), signedUrl);

        ArgumentCaptor<GetObjectPresignRequest> presignCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner, times(1)).presignGetObject(presignCaptor.capture());
        GetObjectPresignRequest captured = presignCaptor.getValue();
        assertEquals(BUCKET_NAME, captured.getObjectRequest().bucket());
        assertEquals(objectKey, captured.getObjectRequest().key());
    }

    @Test
    @DisplayName("Delete object: delegates to s3Client.deleteObject")
    void deleteObject_Success() {
        String objectKey = "complaints/123/photo.jpg";
        storageService.deleteObject(objectKey);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(captor.capture());
        assertEquals(BUCKET_NAME, captor.getValue().bucket());
        assertEquals(objectKey, captor.getValue().key());
    }

    @Test
    @DisplayName("Fallback to LocalStorageService when S3 credentials are absent")
    void fallbackToLocalStorage_WhenUnconfigured() throws Exception {
        NeonS3StorageService unconfiguredService = new NeonS3StorageService(
                "", "", "", "", "",
                localStorageService,
                null,
                null
        );

        assertFalse(unconfiguredService.isCloudStorageConfigured());

        byte[] jpegBytes = TestImageHelper.createValidJpeg(10, 10);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes
        );

        UUID complaintId = UUID.randomUUID();
        when(localStorageService.storeImage(file, "complaints/" + complaintId, null))
                .thenReturn(new StorageResult("complaints/" + complaintId + "/local.jpg", "LOCAL", "/api/complaints/photos/files/local.jpg", "test.jpg", jpegBytes.length, "image/jpeg", "hash"));

        StorageResult res = unconfiguredService.storeImage(file, complaintId);
        assertEquals("LOCAL", res.storageProvider());
        verify(localStorageService, times(1)).storeImage(file, "complaints/" + complaintId, null);
    }

    @Test
    @DisplayName("Strict production fail-fast: validateConfigurationOnStartup throws IllegalStateException when STORAGE_PROVIDER=neon and credentials missing")
    void startupValidation_FailsFastWhenCredentialsMissingInNeonMode() {
        NeonS3StorageService prodService = new NeonS3StorageService(localStorageService);
        // Using reflection to set configuredProvider = "neon" without credentials
        org.springframework.test.util.ReflectionTestUtils.setField(prodService, "configuredProvider", "neon");

        IllegalStateException ex = assertThrows(IllegalStateException.class, prodService::validateConfigurationOnStartup);
        assertTrue(ex.getMessage().contains("Strict production startup validation failed"));
        assertTrue(ex.getMessage().contains("AWS_ACCESS_KEY_ID"));
    }

    @Test
    @DisplayName("Refuses silent fallback to local storage when STORAGE_PROVIDER=neon")
    void refusesSilentFallback_WhenStorageProviderIsNeon() throws Exception {
        NeonS3StorageService prodService = new NeonS3StorageService(localStorageService);
        org.springframework.test.util.ReflectionTestUtils.setField(prodService, "configuredProvider", "neon");

        byte[] jpegBytes = TestImageHelper.createValidJpeg(10, 10);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                jpegBytes
        );

        UUID complaintId = UUID.randomUUID();

        // Should throw IllegalStateException on storeImage because credentials are not configured and provider is neon
        assertThrows(IllegalStateException.class, () -> prodService.storeImage(file, complaintId));
        verify(localStorageService, never()).storeImage(any(), any(), any());
    }
}
