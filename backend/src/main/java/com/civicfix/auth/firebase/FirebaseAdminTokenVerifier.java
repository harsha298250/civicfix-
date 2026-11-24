package com.civicfix.auth.firebase;

import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.config.FirebaseProperties;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminTokenVerifier.class);
    private final FirebaseProperties firebaseProperties;

    public FirebaseAdminTokenVerifier(FirebaseProperties firebaseProperties) {
        this.firebaseProperties = firebaseProperties;
    }

    @Override
    public FirebaseTokenClaims verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new UnauthorizedException("Firebase ID token must not be null or blank.");
        }

        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Token verification cannot proceed with Google servers.");
            throw new UnauthorizedException("Firebase authentication service is not configured on the server.");
        }

        try {
            // verifyIdToken(idToken, true) checks whether token was revoked
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idToken, true);

            // Audience validation against configured project ID
            String configuredProjectId = firebaseProperties.getProjectId();
            if (configuredProjectId != null && !configuredProjectId.isBlank()) {
                String audience = (String) token.getClaims().get("aud");
                if (audience != null && !audience.equalsIgnoreCase(configuredProjectId)) {
                    log.warn("Firebase ID token audience mismatch: expected {} but was {}", configuredProjectId, audience);
                    throw new UnauthorizedException("Firebase ID token audience mismatch. Invalid project token.");
                }
            }

            return new FirebaseTokenClaims(
                    token.getUid(),
                    token.getEmail(),
                    token.isEmailVerified(),
                    token.getName(),
                    token.getClaims()
            );
        } catch (FirebaseAuthException ex) {
            log.warn("Firebase ID token verification failed [code={}]: {}", ex.getAuthErrorCode(), ex.getMessage());
            String errorCode = ex.getAuthErrorCode() != null ? ex.getAuthErrorCode().name() : "";
            if ("EXPIRED_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                throw new UnauthorizedException("Firebase ID token has expired. Please re-authenticate.");
            } else if ("REVOKED_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                throw new UnauthorizedException("Firebase ID token has been revoked.");
            } else if ("INVALID_ID_TOKEN".equalsIgnoreCase(errorCode)) {
                throw new UnauthorizedException("Invalid Firebase ID token.");
            } else {
                throw new UnauthorizedException("Failed to verify Firebase authentication: " + ex.getMessage());
            }
        }
    }
}
