package com.civicfix.gis;

import com.civicfix.common.util.GeoUtils;
import com.civicfix.gis.dto.ReverseGeocodeResponse;
import com.civicfix.gis.dto.WardValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final int MAX_CACHE_ENTRIES = 5000;
    private final Map<String, ReverseGeocodeResponse> cache = Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, ReverseGeocodeResponse>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ReverseGeocodeResponse> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

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
        loadBoundaries();
    }

    private JsonNode stateBoundaryGeoJson;
    private JsonNode gccZonesGeoJson;
    private final List<List<Double>> stateExteriorRing = new ArrayList<>();
    private final List<ZoneBoundary> zoneBoundaries = new ArrayList<>();

    public static class ZoneBoundary {
        private String zoneNumber;
        private String zoneName;
        private List<String> coveredWards = new ArrayList<>();
        private List<List<Double>> exteriorRing = new ArrayList<>();

        public String getZoneNumber() {
            return zoneNumber;
        }

        public void setZoneNumber(String zoneNumber) {
            this.zoneNumber = zoneNumber;
        }

        public String getZoneName() {
            return zoneName;
        }

        public void setZoneName(String zoneName) {
            this.zoneName = zoneName;
        }

        public List<String> getCoveredWards() {
            return coveredWards;
        }

        public void setCoveredWards(List<String> coveredWards) {
            this.coveredWards = coveredWards;
        }

        public List<List<Double>> getExteriorRing() {
            return exteriorRing;
        }

        public void setExteriorRing(List<List<Double>> exteriorRing) {
            this.exteriorRing = exteriorRing;
        }
    }

    private void loadBoundaries() {
        try (InputStream is = getClass().getResourceAsStream("/gis/boundaries/tamil_nadu_state.geojson")) {
            if (is != null) {
                this.stateBoundaryGeoJson = objectMapper.readTree(is);
                JsonNode feat = stateBoundaryGeoJson.path("features").get(0);
                if (feat != null) {
                    JsonNode coords = feat.path("geometry").path("coordinates").get(0);
                    if (coords != null && coords.isArray()) {
                        for (JsonNode pt : coords) {
                            if (pt.isArray() && pt.size() >= 2) {
                                stateExteriorRing.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
                            }
                        }
                    }
                }
                log.info("Loaded Tamil Nadu state boundary ({} vertices).", stateExteriorRing.size());
            } else {
                log.warn("tamil_nadu_state.geojson not found on classpath.");
            }
        } catch (Exception e) {
            log.error("Failed to load tamil_nadu_state.geojson: {}", e.getMessage(), e);
        }

        try (InputStream is = getClass().getResourceAsStream("/gis/boundaries/chennai_gcc_zones.geojson")) {
            if (is != null) {
                this.gccZonesGeoJson = objectMapper.readTree(is);
                JsonNode features = gccZonesGeoJson.path("features");
                if (features != null && features.isArray()) {
                    for (JsonNode f : features) {
                        ZoneBoundary zb = new ZoneBoundary();
                        zb.setZoneNumber(f.path("properties").path("zoneNumber").asText());
                        zb.setZoneName(f.path("properties").path("zoneName").asText());
                        JsonNode wards = f.path("properties").path("coveredWards");
                        if (wards != null && wards.isArray()) {
                            for (JsonNode w : wards) {
                                zb.getCoveredWards().add(w.asText());
                            }
                        }
                        JsonNode coords = f.path("geometry").path("coordinates").get(0);
                        if (coords != null && coords.isArray()) {
                            List<List<Double>> ring = new ArrayList<>();
                            for (JsonNode pt : coords) {
                                if (pt.isArray() && pt.size() >= 2) {
                                    ring.add(List.of(pt.get(0).asDouble(), pt.get(1).asDouble()));
                                }
                            }
                            zb.setExteriorRing(ring);
                        }
                        zoneBoundaries.add(zb);
                    }
                }
                log.info("Loaded {} Chennai GCC Zone boundaries.", zoneBoundaries.size());
            } else {
                log.warn("chennai_gcc_zones.geojson not found on classpath.");
            }
        } catch (Exception e) {
            log.error("Failed to load chennai_gcc_zones.geojson: {}", e.getMessage(), e);
        }
    }

    public JsonNode getStateBoundaryGeoJson() {
        return stateBoundaryGeoJson;
    }

    public JsonNode getZonesBoundaryGeoJson() {
        return gccZonesGeoJson;
    }

    public List<ZoneBoundary> getZoneBoundaries() {
        return zoneBoundaries;
    }

    public WardValidationResult validateWardContainment(BigDecimal lat, BigDecimal lng, String wardName) {
        if (lat == null || lng == null || !GeoUtils.isValidCoordinatePair(lat, lng)) {
            return WardValidationResult.unavailable("Geographic coordinates are missing or invalid.");
        }

        double dLat = lat.doubleValue();
        double dLng = lng.doubleValue();

        // 1. Evaluate State-level boundary containment (Tamil Nadu)
        if (!stateExteriorRing.isEmpty()) {
            boolean insideState = GeoUtils.isPointInPolygon(dLat, dLng, stateExteriorRing);
            if (!insideState) {
                double distToState = GeoUtils.distanceToPolygonBoundaryMeters(dLat, dLng, stateExteriorRing);
                return WardValidationResult.outsideBounds(
                        wardName,
                        "Coordinates are outside the State of Tamil Nadu administrative boundary.",
                        "Survey of India / Bharat Maps",
                        Math.round(distToState * 10.0) / 10.0
                );
            }
        }

        // 2. Evaluate Municipal Zone containment (GCC Zones I - XV)
        ZoneBoundary matchedZone = null;
        for (ZoneBoundary zb : zoneBoundaries) {
            if (GeoUtils.isPointInPolygon(dLat, dLng, zb.getExteriorRing())) {
                matchedZone = zb;
                break;
            }
        }

        if (matchedZone != null) {
            boolean matches = false;
            if (wardName == null || wardName.isBlank()) {
                matches = true;
            } else {
                Set<Integer> wardNums = extractWardNumbers(wardName);
                if (wardNums.size() > 1 || wardNums.contains(-1)) {
                    matches = false;
                } else if (wardNums.size() == 1) {
                    matches = coversWardNumber(matchedZone, wardNums.iterator().next());
                } else {
                    ZoneBoundary claimedZone = findClaimedZone(wardName);
                    if (claimedZone != null) {
                        matches = claimedZone.getZoneNumber().equalsIgnoreCase(matchedZone.getZoneNumber());
                    }
                }
            }

            if (matches) {
                return WardValidationResult.valid(
                        wardName,
                        "Zone " + matchedZone.getZoneNumber() + " - " + matchedZone.getZoneName(),
                        "Coordinates validated within approximate " + matchedZone.getZoneName() + " zonal envelope (50m tolerance). Cadastral ward boundaries are approximate.",
                        "Greater Chennai Corporation (GCC) Planning Division / OpenStreetMap Envelope (Approximate)"
                );
            } else {
                // Point is in a different zone than claimed by wardName
                double distanceToClaimed = 0.0;
                ZoneBoundary claimed = findClaimedZone(wardName);
                if (claimed != null) {
                    distanceToClaimed = GeoUtils.distanceToPolygonBoundaryMeters(dLat, dLng, claimed.getExteriorRing());
                }
                return WardValidationResult.mismatch(
                        wardName,
                        "Zone " + matchedZone.getZoneNumber() + " - " + matchedZone.getZoneName(),
                        "Coordinates are located in approximate Zone " + matchedZone.getZoneNumber() + " (" + matchedZone.getZoneName() + ") envelope, which does not match selected ward " + wardName + ".",
                        "Greater Chennai Corporation (GCC) Planning Division / OpenStreetMap Envelope (Approximate)",
                        Math.round(distanceToClaimed * 10.0) / 10.0
                );
            }
        }

        // 3. Point is inside Tamil Nadu state boundary, but outside Chennai GCC polygon
        ZoneBoundary claimedZone = findClaimedZone(wardName);

        if (claimedZone != null) {
            double distToClaimed = GeoUtils.distanceToPolygonBoundaryMeters(dLat, dLng, claimedZone.getExteriorRing());
            return WardValidationResult.mismatch(
                    wardName,
                    "Outside Chennai Corporation",
                    "Coordinates are located outside Greater Chennai Corporation jurisdiction (" + Math.round(distToClaimed / 1000.0) + " km from " + claimedZone.getZoneName() + ").",
                    "Greater Chennai Corporation (GCC) Planning Division / OpenStreetMap Envelope (Approximate)",
                    Math.round(distToClaimed * 10.0) / 10.0
            );
        }

        double distToGcc = Double.MAX_VALUE;
        for (ZoneBoundary zb : zoneBoundaries) {
            double d = GeoUtils.distanceToPolygonBoundaryMeters(dLat, dLng, zb.getExteriorRing());
            if (d < distToGcc) {
                distToGcc = d;
            }
        }

        return WardValidationResult.outsideGcc(
                wardName,
                "Coordinates are located within Tamil Nadu state boundaries but outside Greater Chennai Corporation jurisdiction (GCC Zones I - XV). Cadastral boundaries for this non-Chennai jurisdiction are not currently ingested.",
                "Greater Chennai Corporation (GCC) / Survey of India (Approximate)",
                distToGcc == Double.MAX_VALUE ? 0.0 : Math.round(distToGcc * 10.0) / 10.0
        );
    }

    private static final Pattern WARD_EXPLICIT_PATTERN = Pattern.compile(
            "(?i)\\b(?:ward|wrd|w)[\\s\\.\\-#]*([0-9]{1,3})\\b"
    );

    private static final Pattern ZONE_EXPLICIT_PATTERN = Pattern.compile(
            "(?i)\\b(?:zone|z)[\\s\\.\\-#]*([0-9]{1,2}|xv|xiv|xiii|xii|xi|x|ix|viii|vii|vi|v|iv|iii|ii|i)\\b"
    );

    private static final Map<String, String> ROMAN_TO_ZONE_NUM = Map.ofEntries(
            Map.entry("i", "I"),
            Map.entry("ii", "II"),
            Map.entry("iii", "III"),
            Map.entry("iv", "IV"),
            Map.entry("v", "V"),
            Map.entry("vi", "VI"),
            Map.entry("vii", "VII"),
            Map.entry("viii", "VIII"),
            Map.entry("ix", "IX"),
            Map.entry("x", "X"),
            Map.entry("xi", "XI"),
            Map.entry("xii", "XII"),
            Map.entry("xiii", "XIII"),
            Map.entry("xiv", "XIV"),
            Map.entry("xv", "XV")
    );

    private static final Map<Integer, String> ARABIC_TO_ZONE_NUM = Map.ofEntries(
            Map.entry(1, "I"),
            Map.entry(2, "II"),
            Map.entry(3, "III"),
            Map.entry(4, "IV"),
            Map.entry(5, "V"),
            Map.entry(6, "VI"),
            Map.entry(7, "VII"),
            Map.entry(8, "VIII"),
            Map.entry(9, "IX"),
            Map.entry(10, "X"),
            Map.entry(11, "XI"),
            Map.entry(12, "XII"),
            Map.entry(13, "XIII"),
            Map.entry(14, "XIV"),
            Map.entry(15, "XV")
    );

    public static Set<Integer> extractWardNumbers(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptySet();
        }
        Matcher matcher = WARD_EXPLICIT_PATTERN.matcher(input);
        Set<Integer> wards = new LinkedHashSet<>();
        while (matcher.find()) {
            try {
                int val = Integer.parseInt(matcher.group(1));
                if (val >= 1 && val <= 200) {
                    wards.add(val);
                } else {
                    wards.add(-1); // out-of-range sentinel
                }
            } catch (NumberFormatException ignored) {}
        }
        return wards;
    }

    public static Integer extractSingleWardNumber(String input) {
        Set<Integer> wards = extractWardNumbers(input);
        if (wards.contains(-1) || wards.size() != 1) {
            return null;
        }
        return wards.iterator().next();
    }

    public boolean coversWardNumber(ZoneBoundary zb, int wardNumber) {
        if (zb == null || zb.getCoveredWards() == null) {
            return false;
        }
        for (String cw : zb.getCoveredWards()) {
            Integer cwNum = extractSingleWardNumber(cw);
            if (cwNum != null && cwNum == wardNumber) {
                return true;
            }
        }
        return false;
    }

    private ZoneBoundary matchZoneByName(String input) {
        if (input == null || input.isBlank()) return null;
        String lower = input.toLowerCase();
        for (ZoneBoundary zb : zoneBoundaries) {
            String name = zb.getZoneName().toLowerCase();
            if (lower.contains(name)) {
                return zb;
            }
        }
        return null;
    }

    private ZoneBoundary matchZoneByNumber(String input) {
        if (input == null || input.isBlank()) return null;
        Matcher m = ZONE_EXPLICIT_PATTERN.matcher(input);
        if (m.find()) {
            String token = m.group(1).toLowerCase();
            String zoneNum = null;
            if (ROMAN_TO_ZONE_NUM.containsKey(token)) {
                zoneNum = ROMAN_TO_ZONE_NUM.get(token);
            } else {
                try {
                    int arabic = Integer.parseInt(token);
                    zoneNum = ARABIC_TO_ZONE_NUM.get(arabic);
                } catch (NumberFormatException ignored) {}
            }
            if (zoneNum != null) {
                for (ZoneBoundary zb : zoneBoundaries) {
                    if (zb.getZoneNumber().equalsIgnoreCase(zoneNum)) {
                        return zb;
                    }
                }
            }
        }
        return null;
    }

    public ZoneBoundary findClaimedZone(String wardName) {
        if (wardName == null || wardName.isBlank()) {
            return null;
        }
        // 1. Multiple conflicting or out-of-range ward claims reject as ambiguous/invalid
        Set<Integer> wardNums = extractWardNumbers(wardName);
        if (wardNums.contains(-1) || wardNums.size() > 1) {
            log.warn("Ambiguous or invalid ward claims detected in '{}': {}", wardName, wardNums);
            return null;
        }

        // 2. Check for explicit zone name (e.g. "Adyar", "Anna Nagar", "Perungudi")
        ZoneBoundary nameZone = matchZoneByName(wardName);
        if (nameZone != null) {
            return nameZone;
        }

        // 3. Single exact ward number lookup (e.g. "Ward 1", "Ward 180")
        if (wardNums.size() == 1) {
            int targetWard = wardNums.iterator().next();
            for (ZoneBoundary zb : zoneBoundaries) {
                if (coversWardNumber(zb, targetWard)) {
                    return zb;
                }
            }
            return null;
        }

        // 4. Check for explicit zone number (e.g. "Zone XIII", "Zone 13")
        return matchZoneByNumber(wardName);
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

                // Cache successful response (LRU eviction bounded)
                cache.put(cacheKey, result);
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
