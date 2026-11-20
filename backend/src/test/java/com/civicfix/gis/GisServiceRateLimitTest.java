package com.civicfix.gis;

import com.civicfix.gis.dto.ReverseGeocodeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("GIS Service: Application-Wide Nominatim Rate Limiting & Cooldown Tests")
class GisServiceRateLimitTest {

    private ObjectMapper objectMapper;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockSuccessResponse;

    private static final String SUCCESS_JSON = """
            {
              "display_name": "Anna Nagar, Chennai, Tamil Nadu, 600040, India",
              "address": {
                "road": "2nd Avenue",
                "suburb": "Anna Nagar",
                "city_district": "Ward 08",
                "city": "Chennai",
                "state": "Tamil Nadu",
                "postcode": "600040",
                "country": "India"
              }
            }
            """;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        mockHttpClient = mock(HttpClient.class);
        mockSuccessResponse = mock(HttpResponse.class);

        when(mockSuccessResponse.statusCode()).thenReturn(200);
        when(mockSuccessResponse.body()).thenReturn(SUCCESS_JSON);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockSuccessResponse);
    }

    @Test
    @DisplayName("Rate Limit 1: Consecutive requests are paced by minIntervalMs across threads")
    void consecutiveRequestsArePacedByRateLimiter() throws Exception {
        long minInterval = 150L; // Short interval for deterministic, fast unit test
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, minInterval, 5000L, 2000L);

        List<Long> invocationTimestamps = Collections.synchronizedList(new ArrayList<>());
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    invocationTimestamps.add(System.currentTimeMillis());
                    return mockSuccessResponse;
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ReverseGeocodeResponse> f1 = executor.submit(() -> gisService.reverseGeocode(13.0827, 80.2707));
        Future<ReverseGeocodeResponse> f2 = executor.submit(() -> gisService.reverseGeocode(13.0850, 80.2750));

        ReverseGeocodeResponse r1 = f1.get(5, TimeUnit.SECONDS);
        ReverseGeocodeResponse r2 = f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertEquals(2, invocationTimestamps.size());

        long diff = Math.abs(invocationTimestamps.get(1) - invocationTimestamps.get(0));
        assertTrue(diff >= minInterval - 25, "Subsequent request must be paced by minimum interval (expected >= " + (minInterval - 25) + "ms, was " + diff + "ms)");
        verify(mockHttpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("Rate Limit 2: Concurrent requests from different callers share the exact same limiter")
    void requestsFromDifferentCallersShareSameLimit() throws Exception {
        long minInterval = 100L;
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, minInterval, 5000L, 2000L);

        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Long> invocationTimes = Collections.synchronizedList(new ArrayList<>());

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(inv -> {
                    invocationTimes.add(System.currentTimeMillis());
                    return mockSuccessResponse;
                });

        List<Future<ReverseGeocodeResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final double lat = 13.0800 + (i * 0.01);
            final double lng = 80.2700 + (i * 0.01);
            futures.add(executor.submit(() -> {
                startLatch.await();
                return gisService.reverseGeocode(lat, lng);
            }));
        }

        startLatch.countDown(); // Release all 3 threads simultaneously

        for (Future<ReverseGeocodeResponse> f : futures) {
            ReverseGeocodeResponse resp = f.get(5, TimeUnit.SECONDS);
            assertTrue(resp.isSuccess());
        }
        executor.shutdown();

        assertEquals(threadCount, invocationTimes.size());
        Collections.sort(invocationTimes);

        // Verify that consecutive calls are spaced by approximately minInterval
        for (int i = 1; i < invocationTimes.size(); i++) {
            long diff = invocationTimes.get(i) - invocationTimes.get(i - 1);
            assertTrue(diff >= minInterval - 20, "Call " + i + " was spaced by only " + diff + "ms; expected >= " + (minInterval - 20) + "ms");
        }
    }

    @Test
    @DisplayName("Cache Check: Cached results return immediately without acquiring limiter or hitting Nominatim")
    void cachedResultsDoNotUnnecessarilyCallNominatim() throws Exception {
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, 1000L, 5000L, 2000L);

        // First call populates cache
        ReverseGeocodeResponse r1 = gisService.reverseGeocode(13.0827, 80.2707);
        assertTrue(r1.isSuccess());
        verify(mockHttpClient, times(1)).send(any(), any());

        // Second call with same coordinates (within ~11m precision key)
        long before = System.currentTimeMillis();
        ReverseGeocodeResponse r2 = gisService.reverseGeocode(13.082701, 80.270701);
        long duration = System.currentTimeMillis() - before;

        assertTrue(r2.isSuccess());
        assertEquals(r1.getDisplayName(), r2.getDisplayName());
        // Verify no second network call was made
        verify(mockHttpClient, times(1)).send(any(), any());
        assertTrue(duration < 100, "Cached lookup should be instantaneous (< 100ms, was " + duration + "ms)");
    }

    @Test
    @DisplayName("Cooldown / Storm Defense: Upstream HTTP 429 triggers cooldown window that suppresses retry storm")
    @SuppressWarnings("unchecked")
    void failedRequestsDoNotCauseRetryStorm() throws Exception {
        HttpResponse<String> mock429Response = mock(HttpResponse.class);
        when(mock429Response.statusCode()).thenReturn(429);
        when(mock429Response.body()).thenReturn("Too Many Requests");

        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mock429Response);

        long cooldownMs = 3000L;
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, 50L, 5000L, cooldownMs);

        // 1st request hits 429
        ReverseGeocodeResponse r1 = gisService.reverseGeocode(13.0827, 80.2707);
        assertFalse(r1.isSuccess());
        assertTrue(r1.getError().contains("rate limited"));
        verify(mockHttpClient, times(1)).send(any(), any());

        // Subsequent requests within the cooldown window must NOT hit the network
        ReverseGeocodeResponse r2 = gisService.reverseGeocode(13.0900, 80.2800);
        assertFalse(r2.isSuccess());
        assertTrue(r2.getError().contains("cooldown"));

        ReverseGeocodeResponse r3 = gisService.reverseGeocode(13.0950, 80.2850);
        assertFalse(r3.isSuccess());
        assertTrue(r3.getError().contains("cooldown"));

        // Only the initial call touched the mock network; subsequent storm requests were short-circuited
        verify(mockHttpClient, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("Fallback Preservation: Invalid coordinates and network timeouts return graceful fallback DTO")
    @SuppressWarnings("unchecked")
    void existingGeocodingFallbackBehaviorStillWorks() throws Exception {
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, 50L, 5000L, 2000L);

        // Null coordinates
        ReverseGeocodeResponse nullResp = gisService.reverseGeocode(null, null);
        assertFalse(nullResp.isSuccess());
        assertTrue(nullResp.getError().contains("must not be null"));

        // Out of range coordinates
        ReverseGeocodeResponse outOfRange = gisService.reverseGeocode(195.0, 80.0);
        assertFalse(outOfRange.isSuccess());
        assertTrue(outOfRange.getError().contains("out of geographic range"));

        // Network error
        when(mockHttpClient.send(any(), any())).thenThrow(new IOException("Simulated network connection timeout"));
        ReverseGeocodeResponse networkErr = gisService.reverseGeocode(13.0827, 80.2707);
        assertFalse(networkErr.isSuccess());
        assertTrue(networkErr.getError().contains("Simulated network connection timeout"));
    }

    @Test
    @DisplayName("Queue Timeout: Requests that exceed maxWaitMs yield graceful fallback without crashing")
    @SuppressWarnings("unchecked")
    void queueTimeoutYieldsGracefulFallback() throws Exception {
        long minInterval = 1000L;
        long maxWait = 100L; // Very short max wait to induce timeout
        GisService gisService = new GisService(objectMapper, mockHttpClient, true, minInterval, maxWait, 2000L);

        CountDownLatch firstInFlight = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        when(mockHttpClient.send(any(), any())).thenAnswer(inv -> {
            firstInFlight.countDown();
            releaseFirst.await(3, TimeUnit.SECONDS);
            return mockSuccessResponse;
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ReverseGeocodeResponse> f1 = executor.submit(() -> gisService.reverseGeocode(13.0827, 80.2707));

        firstInFlight.await(2, TimeUnit.SECONDS); // Ensure first request is holding lock

        // Second request arrives while first is holding lock, with maxWait = 100ms
        Future<ReverseGeocodeResponse> f2 = executor.submit(() -> gisService.reverseGeocode(13.0899, 80.2799));
        ReverseGeocodeResponse r2 = f2.get(2, TimeUnit.SECONDS);

        // Release first request
        releaseFirst.countDown();
        ReverseGeocodeResponse r1 = f1.get(2, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(r1.isSuccess());
        assertFalse(r2.isSuccess());
        assertTrue(r2.getError().contains("timeout"));
    }
}
