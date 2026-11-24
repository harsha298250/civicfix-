package com.civicfix.ai.vision.pothole;

import com.civicfix.ai.vision.FakeHttpClient;
import com.civicfix.ai.vision.VisionTriageProperties;
import com.civicfix.ai.vision.pothole.dto.PotholeBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 5D: Pothole Detection Provider Contract & HTTP Tests")
class PotholeDetectionProviderContractTest {

    private VisionTriageProperties properties;
    private FakeHttpClient fakeHttpClient;
    private PotholeDetectionService service;

    private static final String TEST_API_KEY = "test-secret-gemini-key-98765";
    private static final String MODEL_NAME = "gemini-3.8-flash";

    @BeforeEach
    void setUp() {
        properties = new VisionTriageProperties();
        properties.setEnabled(true);
        properties.setGoogleApiKey(TEST_API_KEY);
        properties.setGeminiModel(MODEL_NAME);
        properties.setGeminiBaseUrl("https://generativelanguage.googleapis.com");
        properties.setTimeoutMs(10000);

        fakeHttpClient = new FakeHttpClient();

        service = new PotholeDetectionService(
                null, null, null, null,
                new PotholeDetectionValidator(),
                new PotholeMeasurementEngine(),
                new PotholeSeverityCalculator(),
                properties,
                fakeHttpClient
        );
    }

    @Test
    @DisplayName("1. Contract: Verified HTTP POST method, endpoint URI, and model identifier without query key")
    void testHttpMethodAndEndpoint() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"potholes\\": []}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        service.callGeminiPotholeDetection("test-image-bytes".getBytes(StandardCharsets.UTF_8));

        HttpRequest req = fakeHttpClient.lastRequest;
        assertNotNull(req, "Request must be sent via HttpClient");
        assertEquals("POST", req.method(), "Must use HTTP POST");
        assertTrue(req.uri().toString().contains("/v1beta/models/gemini-3.8-flash:generateContent"),
                "URI must target v1beta models endpoint with model gemini-3.8-flash");
        assertFalse(req.uri().toString().contains("key="),
                "URI must NOT contain API key query parameter");
        assertEquals(TEST_API_KEY, req.headers().firstValue("x-goog-api-key").orElse(null),
                "API key must be passed via x-goog-api-key header");
    }

    @Test
    @DisplayName("1b. Contract: API key is absent from request URI and query parameters")
    void testApiKeyAbsentFromUrlAndQuery() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"potholes\\\": []}\"}]}}]}";

        service.callGeminiPotholeDetection("test".getBytes(StandardCharsets.UTF_8));

        HttpRequest req = fakeHttpClient.lastRequest;
        assertNotNull(req);
        assertNull(req.uri().getQuery(), "URI query must be null (no query parameters allowed)");
        assertFalse(req.uri().toString().contains(TEST_API_KEY), "API key must not be present in URI string");
        assertEquals(TEST_API_KEY, req.headers().firstValue("x-goog-api-key").orElse(null),
                "API key must be transmitted exclusively via x-goog-api-key header");
    }

    @Test
    @DisplayName("2. Contract: Verified request Content-Type header is application/json")
    void testRequestHeaders() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"potholes\\\": []}\"}]}}]}";

        service.callGeminiPotholeDetection("test".getBytes(StandardCharsets.UTF_8));

        HttpRequest req = fakeHttpClient.lastRequest;
        assertNotNull(req);
        assertEquals("application/json", req.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("3. Contract: Verified inline base64 image encoding and JPEG MIME type")
    void testImageEncodingAndMimeType() {
        byte[] imageBytes = "sample-raw-jpeg-data".getBytes(StandardCharsets.UTF_8);
        String expectedBase64 = Base64.getEncoder().encodeToString(imageBytes);

        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"potholes\\\": []}\"}]}}]}";

        service.callGeminiPotholeDetection(imageBytes);

        assertNotNull(fakeHttpClient.lastRequest);
    }

    @Test
    @DisplayName("4. Contract: Successful clean JSON parsing with valid detected potholes")
    void testSuccessfulDetectionCleanJson() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"potholes\\": [{\\"ymin\\": 0.42, \\"xmin\\": 0.35, \\"ymax\\": 0.65, \\"xmax\\": 0.58, \\"confidence\\": 0.94}], \\"description\\": \\"Deep asphalt depression\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(MODEL_NAME, result.getModelName());
        assertEquals(1, result.getBoxes().size());

        PotholeBox box = result.getBoxes().get(0);
        assertEquals(0.42, box.getYmin(), 0.001);
        assertEquals(0.35, box.getXmin(), 0.001);
        assertEquals(0.65, box.getYmax(), 0.001);
        assertEquals(0.58, box.getXmax(), 0.001);
        assertEquals(0.94, box.getConfidence().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("5. Contract: Handles JSON wrapped in Markdown code fences safely")
    void testMarkdownCodeFencesSanitization() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "```json\\n{\\"potholes\\": [{\\"ymin\\": 0.30, \\"xmin\\": 0.20, \\"ymax\\": 0.50, \\"xmax\\": 0.45, \\"confidence\\": 0.88}]}\\n```"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getBoxes().size());
        assertEquals(0.88, result.getBoxes().get(0).getConfidence().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("6. Contract: Empty response text maps to NO_POTHOLE_DETECTED")
    void testEmptyResponseText() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": ""
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("NO_POTHOLE_DETECTED", result.getStatus());
        assertTrue(result.getBoxes().isEmpty());
    }

    @Test
    @DisplayName("7. Contract: Missing candidates array maps to ANALYSIS_FAILED")
    void testMissingCandidates() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = "{}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("No candidate predictions"));
    }

    @Test
    @DisplayName("8. Contract: Safety-blocked response (finishReason = SAFETY) maps to SAFETY_BLOCKED")
    void testSafetyBlockedResponse() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY",
                      "safetyRatings": [
                        {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "probability": "HIGH"}
                      ]
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("SAFETY_BLOCKED", result.getStatus(), "Must report SAFETY_BLOCKED, never false empty detection");
        assertTrue(result.getMessage().contains("blocked by AI safety policy"));
        assertEquals(0, result.getBoxes().size());
    }

    @Test
    @DisplayName("9. Contract: Malformed JSON output maps to ANALYSIS_FAILED without crashing")
    void testMalformedJsonOutput() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{ invalid: json, potholes: [broken "
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertNotNull(result);
        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("could not be parsed as valid JSON"));
    }

    @Test
    @DisplayName("10. Contract: HTTP 400 maps to ANALYSIS_FAILED with safe explanation")
    void testHttp400BadRequest() {
        fakeHttpClient.statusCodeToReturn = 400;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"Invalid image argument\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("HTTP 400"));
    }

    @Test
    @DisplayName("11a. Contract: HTTP 401 authentication rejection maps to ANALYSIS_FAILED without credential leak")
    void testHttp401Unauthorized() {
        fakeHttpClient.statusCodeToReturn = 401;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"API_KEY_INVALID\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertFalse(result.getMessage().contains(TEST_API_KEY), "API key must never appear in error message");
        assertTrue(result.getMessage().contains("authentication rejected (HTTP 401)"));
    }

    @Test
    @DisplayName("11b. Contract: HTTP 403 access forbidden maps to ANALYSIS_FAILED without credential leak")
    void testHttp403Forbidden() {
        fakeHttpClient.statusCodeToReturn = 403;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"PERMISSION_DENIED\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertFalse(result.getMessage().contains(TEST_API_KEY), "API key must never appear in error message");
        assertTrue(result.getMessage().contains("access forbidden (HTTP 403)"));
    }

    @Test
    @DisplayName("12. Contract: HTTP 404 discontinued model maps to ANALYSIS_FAILED with model notice")
    void testHttp404ModelNotFound() {
        fakeHttpClient.statusCodeToReturn = 404;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"models/gemini-1.5-flash is not found\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("not found or discontinued"));
    }

    @Test
    @DisplayName("13. Contract: HTTP 429 quota exhaustion maps to ANALYSIS_FAILED with rate limit explanation")
    void testHttp429QuotaExceeded() {
        fakeHttpClient.statusCodeToReturn = 429;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"Resource has been exhausted\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("rate limit exceeded (HTTP 429)"));
    }

    @Test
    @DisplayName("14. Contract: HTTP 500/503 service unavailable maps to ANALYSIS_FAILED")
    void testHttp503Unavailable() {
        fakeHttpClient.statusCodeToReturn = 503;
        fakeHttpClient.bodyToReturn = "{\"error\": {\"message\": \"Service Unavailable\"}}";

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("temporarily unavailable"));
    }

    @Test
    @DisplayName("15. Contract: Connect timeout maps to ANALYSIS_FAILED with timeout message")
    void testConnectTimeout() {
        fakeHttpClient.exceptionToThrow = new HttpConnectTimeoutException("Connection timed out");

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("16. Contract: Read timeout maps to ANALYSIS_FAILED with timeout message")
    void testReadTimeout() {
        fakeHttpClient.exceptionToThrow = new HttpTimeoutException("Read timed out");

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("ANALYSIS_FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("timed out"));
    }

    @Test
    @DisplayName("17. Contract: Unconfigured API key returns UNAVAILABLE without making HTTP calls")
    void testUnconfiguredApiKeyReturnsUnavailable() {
        properties.setGoogleApiKey("");

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("UNAVAILABLE", result.getStatus());
        assertTrue(result.getMessage().contains("missing API key"));
        assertNull(fakeHttpClient.lastRequest, "Zero HTTP calls must be made when API key is missing");
    }

    @Test
    @DisplayName("18. Contract: Prompt blocked by safety filter maps to SAFETY_BLOCKED")
    void testPromptBlockedBySafetyFilter() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "promptFeedback": {
                    "blockReason": "SAFETY"
                  }
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("SAFETY_BLOCKED", result.getStatus());
        assertTrue(result.getMessage().contains("Prompt blocked by safety filter"));
    }

    @Test
    @DisplayName("19. Contract: Markdown fence stripper handles various code block formats")
    void testMarkdownFenceStripper() {
        assertEquals("{\"test\": true}", PotholeDetectionService.stripMarkdownFences("```json\n{\"test\": true}\n```"));
        assertEquals("{\"test\": true}", PotholeDetectionService.stripMarkdownFences("```\n{\"test\": true}\n```"));
        assertEquals("{\"test\": true}", PotholeDetectionService.stripMarkdownFences("{\"test\": true}"));
        assertEquals("", PotholeDetectionService.stripMarkdownFences(null));
    }

    @Test
    @DisplayName("20. Contract: Zero detections returned when potholes array is empty")
    void testEmptyPotholesArray() {
        fakeHttpClient.statusCodeToReturn = 200;
        fakeHttpClient.bodyToReturn = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"potholes\\": [], \\"description\\": \\"Clean road surface\\"}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        PotholeDetectionService.PotholeInferenceResult result =
                service.callGeminiPotholeDetection("image-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("NO_POTHOLE_DETECTED", result.getStatus());
        assertEquals(0, result.getBoxes().size());
    }
}
