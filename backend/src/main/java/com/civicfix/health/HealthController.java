package com.civicfix.health;

import com.civicfix.ai.vision.VisionTriageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private VisionTriageProperties visionProperties;

    @Value("${civicfix.storage.provider:local}")
    private String storageProvider;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        boolean dbUp = true;
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection()) {
                dbUp = conn != null && conn.isValid(2);
            } catch (Exception ex) {
                dbUp = false;
            }
        }

        Map<String, String> components = new HashMap<>();
        components.put("database", dbUp ? "UP" : "DOWN");
        components.put("storage", "neon".equalsIgnoreCase(storageProvider) ? "NEON_S3_CONFIGURED" : "LOCAL_FALLBACK");
        components.put("email", (resendApiKey != null && !resendApiKey.isBlank()) ? "RESEND_CONFIGURED" : "UNCONFIGURED");
        boolean visionReady = visionProperties != null && visionProperties.isEnabled()
                && visionProperties.getGoogleApiKey() != null && !visionProperties.getGoogleApiKey().isBlank();
        components.put("aiVision", visionReady ? "ENABLED" : "DISABLED_OR_UNCONFIGURED");

        health.put("status", dbUp ? "UP" : "DEGRADED");
        health.put("service", "CivicFix Platform Backend");
        health.put("version", "1.0.0");
        health.put("timestamp", OffsetDateTime.now().toString());
        health.put("environment", "active");
        health.put("components", components);
        return ResponseEntity.ok(health);
    }
}
