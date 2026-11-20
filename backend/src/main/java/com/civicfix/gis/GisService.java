package com.civicfix.gis;

import com.civicfix.gis.dto.ReverseGeocodeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * GisService — OpenStreetMap Nominatim Compliant Geocoding Service
 * Enforces OSM Nominatim usage policy:
 * 1. Explicit, identifiable User-Agent header (CivicFix-AI-Platform/1.0).
 * 2. Application-wide request rate limiting: strictly <= 1 request/sec across all callers and threads.
 * 3. In-memory cache for coordinates (~11m spatial resolution).
 * 4. Upstream backoff cooldown window if HTTP 429/503 is returned to prevent retry storms.
 * 5. Graceful fallback on network timeout, queue timeout, or rate limits.
 *
 * ARCHITECTURAL LIMITATION & DISTRIBUTED DEPLOYMENT NOTE:
 * The current rate-limiting implementation uses process-local synchronization (fair ReentrantLock)
 * suitable for the current single-instance Docker deployment. If multiple backend replicas
 * are deployed in a clustered or horizontally scaled production environment, rate-limiting
 * must be coordinated across replicas using a distributed token bucket (e.g., Redis or Redisson).
 */
@Service
public class GisService {

    private static final Logger log = LoggerFactory.getLogger(GisService.class);
    public static final String USER_AGENT = "CivicFix-AI-Platform/1.0 (https://civicfix.org; contact@civicfix.org)";
    public static final String NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ReverseGeocodeResponse> cache = new ConcurrentHashMap<>();

    // Configurable rate limit parameters
    private final boolean rateLimiterEnabled;
    private final long minIntervalMs;
    private final long maxWaitMs;
    private final long cooldownMs;

    // Shared thread-safe rate limiter state across all callers and threads
    private final ReentrantLock rateLimitLock = new ReentrantLock(true); // fair FIFO ordering
    private volatile long lastRequestTimestamp = 0L;
    private volatile long cooldownUntilTimestamp = 0L;

    @Autowired
    public GisService(
            ObjectMapper objectMapper,
            @Value("${civicfix.rate-limit.nominatim.enabled:${civicfix.gis.nominatim.enabled:true}}") boolean rateLimiterEnabled,
            @Value("${civicfix.rate-limit.nominatim.min-interval-ms:${civicfix.gis.nominatim.min-interval-ms:1000}}") long minIntervalMs,
            @Value("${civicfix.rate-limit.nominatim.max-wait-ms:${civicfix.gis.nominatim.max-wait-ms:5000}}") long maxWaitMs,
            @Value("${civicfix.rate-limit.nominatim.cooldown-ms:${civicfix.gis.nominatim.cooldown-ms:10000}}") long cooldownMs) {
        this(objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),
                rateLimiterEnabled,
                minIntervalMs,
                maxWaitMs,
                cooldownMs);
    }

    public GisService(ObjectMapper objectMapper) {
        this(objectMapper, true, 1000L, 5000L, 10000L);
    }

    public GisService(
            ObjectMapper objectMapper,
            HttpClient httpClient,
            boolean rateLimiterEnabled,
            long minIntervalMs,
            long maxWaitMs,
            long cooldownMs) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.rateLimiterEnabled = rateLimiterEnabled;
        this.minIntervalMs = minIntervalMs;
        this.maxWaitMs = maxWaitMs;
        this.cooldownMs = cooldownMs;
    }

    public ReverseGeocodeResponse reverseGeocode(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return ReverseGeocodeResponse.fallback(0.0, 0.0, "Coordinates must not be null");
        }
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            return ReverseGeocodeResponse.fallback(lat, lng, "Coordinates out of geographic range");
        }

        // Cache key with 4 decimal places (~11 meters)
        String cacheKey = String.format(java.util.Locale.US, "%.4f:%.4f", lat, lng);
        ReverseGeocodeResponse cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("Nominatim cache hit for key {}", cacheKey);
            return cached;
        }

        // Fast-path cooldown check before lock acquisition to prevent retry storms
        long now = System.currentTimeMillis();
        if (now < cooldownUntilTimestamp) {
            log.warn("Nominatim service is in backoff cooldown until {}. Fast-falling back.", cooldownUntilTimestamp);
            return ReverseGeocodeResponse.fallback(lat, lng, "Geocoding service in backoff cooldown after upstream rate limit; fallback used");
        }

        boolean acquired = false;
        if (rateLimiterEnabled) {
            try {
                acquired = rateLimitLock.tryLock(maxWaitMs, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log.warn("Nominatim rate limiter wait timed out ({} ms) for coordinates ({}, {})", maxWaitMs, lat, lng);
                    return ReverseGeocodeResponse.fallback(lat, lng, "Geocoding request rate limit timeout; fallback used");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Nominatim rate limiter acquisition interrupted for coordinates ({}, {})", lat, lng);
                return ReverseGeocodeResponse.fallback(lat, lng, "Geocoding request interrupted; fallback used");
            }
        }

        try {
            // Re-check cache in case another waiting thread already completed reverse geocoding for this location
            cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            // Re-check cooldown after acquiring lock
            now = System.currentTimeMillis();
            if (now < cooldownUntilTimestamp) {
                return ReverseGeocodeResponse.fallback(lat, lng, "Geocoding service in backoff cooldown; fallback used");
            }

            // Enforce minimum interval between consecutive outbound requests across all callers
            if (rateLimiterEnabled && lastRequestTimestamp > 0) {
                long elapsed = now - lastRequestTimestamp;
                if (elapsed < minIntervalMs) {
                    long sleepMs = minIntervalMs - elapsed;
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return ReverseGeocodeResponse.fallback(lat, lng, "Rate limiter sleep interrupted");
                    }
                }
            }

            String url = String.format(java.util.Locale.US, NOMINATIM_REVERSE_URL, lat, lng);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastRequestTimestamp = System.currentTimeMillis();

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode addr = root.path("address");

                String road = getFirstNonEmpty(addr, "road", "pedestrian", "suburb", "neighbourhood");
                String ward = getFirstNonEmpty(addr, "city_district", "suburb", "ward");
                String city = getFirstNonEmpty(addr, "city", "town", "village", "county", "Tamil Nadu");
                String state = addr.path("state").asText("Tamil Nadu");
                String postcode = addr.path("postcode").asText("");

                StringBuilder fullStreet = new StringBuilder();
                if (!road.isEmpty()) fullStreet.append(road);
                if (!ward.isEmpty()) {
                    if (fullStreet.length() > 0) fullStreet.append(", ");
                    fullStreet.append(ward);
                }
                if (!city.isEmpty()) {
                    if (fullStreet.length() > 0) fullStreet.append(", ");
                    fullStreet.append(city);
                }

                String displayName = root.path("display_name").asText(fullStreet.toString());
                String streetAddress = fullStreet.length() > 0 ? fullStreet.toString() : displayName;

                ReverseGeocodeResponse result = new ReverseGeocodeResponse();
                result.setSuccess(true);
                result.setLat(lat);
                result.setLng(lng);
                result.setDisplayName(displayName);
                result.setStreetAddress(streetAddress);
                result.setCityDistrict(!ward.isEmpty() ? ward : city);
                result.setCity(city);
                result.setState(state);
                result.setPostcode(postcode);

                // Cache successful response (limit size to prevent memory bloat)
                if (cache.size() < 5000) {
                    cache.put(cacheKey, result);
                }
                return result;
            } else if (response.statusCode() == 429 || response.statusCode() == 503) {
                cooldownUntilTimestamp = System.currentTimeMillis() + cooldownMs;
                log.warn("Nominatim rate limit response (HTTP {}). Activating backoff cooldown for {} ms", response.statusCode(), cooldownMs);
                return ReverseGeocodeResponse.fallback(lat, lng, "Upstream geocoding rate limited (HTTP " + response.statusCode() + "); fallback used");
            } else {
                log.warn("Nominatim reverse geocode returned HTTP {}", response.statusCode());
                return ReverseGeocodeResponse.fallback(lat, lng, "Provider status " + response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Nominatim reverse geocoding lookup failed: {}", e.getMessage());
            return ReverseGeocodeResponse.fallback(lat, lng, e.getMessage());
        } finally {
            if (acquired) {
                rateLimitLock.unlock();
            }
        }
    }

    private String getFirstNonEmpty(JsonNode node, String... keys) {
        for (String key : keys) {
            String val = node.path(key).asText("");
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return "";
    }

    // Monitoring and test inspection methods
    public long getLastRequestTimestamp() {
        return lastRequestTimestamp;
    }

    public long getCooldownUntilTimestamp() {
        return cooldownUntilTimestamp;
    }

    public void setCooldownUntilTimestamp(long timestamp) {
        this.cooldownUntilTimestamp = timestamp;
    }

    public boolean isRateLimiterEnabled() {
        return rateLimiterEnabled;
    }

    public long getMinIntervalMs() {
        return minIntervalMs;
    }

    public long getMaxWaitMs() {
        return maxWaitMs;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void clearCache() {
        cache.clear();
    }

    public int getCacheSize() {
        return cache.size();
    }
}
