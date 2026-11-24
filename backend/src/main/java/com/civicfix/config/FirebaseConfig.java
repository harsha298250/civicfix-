package com.civicfix.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private final FirebaseProperties properties;

    public FirebaseConfig(FirebaseProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (!properties.isEnabled()) {
            log.info("Firebase authentication is explicitly disabled via configuration.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized (apps count: {}).", FirebaseApp.getApps().size());
            return;
        }

        try {
            GoogleCredentials credentials = null;

            if (properties.getCredentialsPath() != null && !properties.getCredentialsPath().isBlank()) {
                log.info("Initializing Firebase Admin SDK using service account file: {}", properties.getCredentialsPath());
                try (InputStream is = new FileInputStream(properties.getCredentialsPath().trim())) {
                    credentials = GoogleCredentials.fromStream(is);
                }
            } else if (properties.getCredentialsJson() != null && !properties.getCredentialsJson().isBlank()) {
                log.info("Initializing Firebase Admin SDK using inline JSON credentials from environment.");
                try (InputStream is = new ByteArrayInputStream(properties.getCredentialsJson().trim().getBytes(StandardCharsets.UTF_8))) {
                    credentials = GoogleCredentials.fromStream(is);
                }
            } else {
                log.info("No explicit service account provided; attempting Google Application Default Credentials.");
                try {
                    credentials = GoogleCredentials.getApplicationDefault();
                } catch (Exception e) {
                    log.info("Application Default Credentials not available in this environment: {}. FirebaseAdmin will remain uninitialized until credentials are provided.", e.getMessage());
                }
            }

            if (credentials != null) {
                FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
                if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
                    builder.setProjectId(properties.getProjectId().trim());
                }
                FirebaseApp.initializeApp(builder.build());
                log.info("Firebase Admin SDK successfully initialized for project: {}", properties.getProjectId());
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize Firebase Admin SDK: {}. Firebase verification will fail until credentials are configured.", ex.getMessage());
        }
    }
}
