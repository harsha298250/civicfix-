package com.civicfix.ai.vision;

import com.civicfix.complaint.Complaint;
import com.civicfix.complaint.ComplaintPhoto;
import com.civicfix.complaint.ComplaintPhotoRepository;
import com.civicfix.complaint.ComplaintPriority;
import com.civicfix.complaint.ComplaintRepository;
import com.civicfix.security.ratelimit.RateLimiterService;
import com.civicfix.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.SyncTaskExecutor;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2.4.4: Vision Multi-Photo Consensus & Hardening Tests")
class VisionMultiPhotoConsensusTest {

    static class QueueingFakeHttpClient extends FakeHttpClient {
        private final Queue<String> responses = new LinkedList<>();
        private int callCount = 0;

        void enqueueResponse(String body) {
            responses.add(body);
        }

        int getCallCount() {
            return callCount;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            callCount++;
            this.lastRequest = request;
            String body = responses.isEmpty() ? bodyToReturn : responses.poll();
            return (HttpResponse<T>) new FakeHttpResponse(statusCodeToReturn, body, request);
        }
    }

    private ComplaintRepository complaintRepository;
    private ComplaintPhotoRepository complaintPhotoRepository;
    private StorageService storageService;
    private RateLimiterService rateLimiterService;
    private VisionTriageProperties properties;
    private QueueingFakeHttpClient geminiHttpClient;
    private GeminiVisionTriageProvider geminiProvider;
    private XaiVisionTriageProvider xaiProvider;

    private VisionTriageAsyncService service;

    private UUID complaintId;
    private UUID citizenId;
    private Complaint complaint;
    private List<ComplaintPhoto> photosList;

    // Captured updates
    private String lastUpdatedStatus;
    private String lastSuccessCategory;
    private Integer lastSuccessSeverity;
    private BigDecimal lastSuccessConfidence;
    private String lastSuccessReason;
    private ComplaintPriority lastSuccessPriority;
    private String lastSuccessPrioritySource;

    @BeforeEach
    void setUp() {
        photosList = new ArrayList<>();
        lastUpdatedStatus = null;
        lastSuccessCategory = null;
        lastSuccessSeverity = null;
        lastSuccessConfidence = null;
        lastSuccessReason = null;
        lastSuccessPriority = null;
        lastSuccessPrioritySource = null;

        complaintId = UUID.randomUUID();
        citizenId = UUID.randomUUID();

        complaint = new Complaint();
        complaint.setId(complaintId);
        complaint.setAiDetectedCategory("ROADS");
        complaint.setAiRecommendedPriority(ComplaintPriority.MEDIUM);
        complaint.setVisionConsent(true);
        complaint.setVisionStatus("NOT_RUN");

        properties = new VisionTriageProperties();
        properties.setEnabled(true);
        properties.setProvider("gemini");
        properties.setDailyCap(200);
        properties.setGoogleApiKey("test-key");
        properties.setGeminiModel("gemini-1.5-flash");

        geminiHttpClient = new QueueingFakeHttpClient();

        complaintRepository = (ComplaintRepository) Proxy.newProxyInstance(
                ComplaintRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findById".equals(name)) return Optional.of(complaint);
                    if ("claimForVisionTriage".equals(name)) return 1;
                    if ("countDailyVisionInvocations".equals(name)) return 0L;
                    if ("updateVisionStatus".equals(name)) {
                        lastUpdatedStatus = (String) args[1];
                        return 1;
                    }
                    if ("updateVisionSuccess".equals(name)) {
                        lastSuccessCategory = (String) args[1];
                        lastSuccessSeverity = (Integer) args[2];
                        lastSuccessConfidence = (BigDecimal) args[3];
                        lastSuccessReason = (String) args[4];
                        lastUpdatedStatus = (String) args[6];
                        lastSuccessPrioritySource = (String) args[10];
                        lastSuccessPriority = (ComplaintPriority) args[11];
                        return 1;
                    }
                    return null;
                }
        );

        complaintPhotoRepository = (ComplaintPhotoRepository) Proxy.newProxyInstance(
                ComplaintPhotoRepository.class.getClassLoader(),
                new Class<?>[]{ComplaintPhotoRepository.class},
                (proxy, method, args) -> {
                    if ("findByComplaintIdOrderByCreatedAtAsc".equals(method.getName())) {
                        return photosList;
                    }
                    return Collections.emptyList();
                }
        );

        storageService = (StorageService) Proxy.newProxyInstance(
                StorageService.class.getClassLoader(),
                new Class<?>[]{StorageService.class},
                (proxy, method, args) -> {
                    if ("loadFileAsResource".equals(method.getName())) {
                        return new ByteArrayResource("valid-bytes".getBytes());
                    }
                    return null;
                }
        );

        rateLimiterService = new RateLimiterService();
    }

    private ComplaintPhoto createPhoto(String name, String url, String type, long size) {
        ComplaintPhoto p = new ComplaintPhoto();
        p.setId(UUID.randomUUID());
        p.setComplaint(complaint);
        p.setFileName(name);
        p.setPhotoUrl(url);
        p.setContentType(type);
        p.setFileSize(size);
        p.setCreatedAt(OffsetDateTime.now());
        return p;
    }

    @Test
    @DisplayName("Multi-photo consensus: 2 ROADS vs 1 DRAINAGE picks ROADS and elevates severity to 5")
    void testMultiPhotoConsensusAndSeverityElevation() {
        photosList.add(createPhoto("photo1.jpg", "uploads/photo1.jpg", "image/jpeg", 500000));
        photosList.add(createPhoto("photo2.png", "uploads/photo2.png", "image/png", 600000));
        photosList.add(createPhoto("photo3.jpg", "uploads/photo3.jpg", "image/jpeg", 700000));

        // Mock Gemini responses for the 3 photos
        String resp1 = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"category\\\":\\\"ROADS\\\",\\\"severity\\\":3,\\\"confidence\\\":0.90,\\\"reason\\\":\\\"Pothole in middle lane\\\"}\\n```\"}]}}]}";
        String resp2 = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"category\\\":\\\"DRAINAGE\\\",\\\"severity\\\":2,\\\"confidence\\\":0.80,\\\"reason\\\":\\\"Minor curb blockage\\\"}\\n```\"}]}}]}";
        String resp3 = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"category\\\":\\\"ROADS\\\",\\\"severity\\\":5,\\\"confidence\\\":0.94,\\\"reason\\\":\\\"Deep crater hazard with exposed reinforcement\\\"}\\n```\"}]}}]}";

        geminiHttpClient.enqueueResponse(resp1);
        geminiHttpClient.enqueueResponse(resp2);
        geminiHttpClient.enqueueResponse(resp3);

        geminiProvider = new GeminiVisionTriageProvider(properties, geminiHttpClient);
        service = new VisionTriageAsyncService(
                complaintRepository, complaintPhotoRepository, storageService,
                rateLimiterService, properties, geminiProvider, xaiProvider, new SyncTaskExecutor()
        );

        service.processVisionTriage(complaintId, citizenId);

        assertEquals("OK", lastUpdatedStatus);
        assertEquals("ROADS", lastSuccessCategory, "Plurality category should be ROADS");
        assertEquals(5, lastSuccessSeverity, "Highest severity (5) across photos should be selected");
        assertEquals(ComplaintPriority.HIGH, lastSuccessPriority, "MEDIUM priority should be elevated to HIGH due to severity 5");
        assertEquals("VISION_SEVERITY_5", lastSuccessPrioritySource);
        assertNotNull(lastSuccessConfidence);
        assertTrue(lastSuccessReason.contains("Multi-photo consensus (3 photos)"));
        assertTrue(lastSuccessReason.contains("P1:"));
        assertTrue(lastSuccessReason.contains("P2:"));
        assertTrue(lastSuccessReason.contains("P3:"));
    }

    @Test
    @DisplayName("WebP image is skipped and only valid JPEG/PNG candidates are processed")
    void testWebpSkippedInMultiPhoto() {
        photosList.add(createPhoto("photo1.webp", "uploads/photo1.webp", "image/webp", 300000));
        photosList.add(createPhoto("photo2.jpg", "uploads/photo2.jpg", "image/jpeg", 500000));

        String resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"category\\\":\\\"STREET_LIGHT\\\",\\\"severity\\\":2,\\\"confidence\\\":0.85,\\\"reason\\\":\\\"Dark pole fixture\\\"}\\n```\"}]}}]}";
        geminiHttpClient.enqueueResponse(resp);

        geminiProvider = new GeminiVisionTriageProvider(properties, geminiHttpClient);
        service = new VisionTriageAsyncService(
                complaintRepository, complaintPhotoRepository, storageService,
                rateLimiterService, properties, geminiProvider, xaiProvider, new SyncTaskExecutor()
        );

        service.processVisionTriage(complaintId, citizenId);

        assertEquals("OK", lastUpdatedStatus);
        assertEquals("STREET_LIGHT", lastSuccessCategory);
        assertEquals(2, lastSuccessSeverity);
        assertEquals(1, geminiHttpClient.getCallCount(), "Only the valid JPEG should be sent to Gemini, WebP skipped");
    }

    @Test
    @DisplayName("Bounded to at most 3 candidate photos even when 5 photos exist")
    void testAtMostThreePhotosProcessed() {
        for (int i = 1; i <= 5; i++) {
            photosList.add(createPhoto("p" + i + ".jpg", "uploads/p" + i + ".jpg", "image/jpeg", 400000));
        }

        String resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```json\\n{\\\"category\\\":\\\"ROADS\\\",\\\"severity\\\":3,\\\"confidence\\\":0.88,\\\"reason\\\":\\\"Road defect\\\"}\\n```\"}]}}]}";
        geminiHttpClient.enqueueResponse(resp);
        geminiHttpClient.enqueueResponse(resp);
        geminiHttpClient.enqueueResponse(resp);

        geminiProvider = new GeminiVisionTriageProvider(properties, geminiHttpClient);
        service = new VisionTriageAsyncService(
                complaintRepository, complaintPhotoRepository, storageService,
                rateLimiterService, properties, geminiProvider, xaiProvider, new SyncTaskExecutor()
        );

        service.processVisionTriage(complaintId, citizenId);

        assertEquals("OK", lastUpdatedStatus);
        assertEquals(3, geminiHttpClient.getCallCount(), "Maximum 3 photos should be processed for triage");
    }
}
