package com.civicfix.auth.google;

import com.civicfix.common.exception.UnauthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;

@Component
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierImpl.class);

    @Value("${civicfix.auth.google.client-id:${GOOGLE_CLIENT_ID:}}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleTokenVerifierImpl() {}

    public GoogleTokenVerifierImpl(String googleClientId) {
        this.googleClientId = googleClientId;
        initVerifier();
    }

    private synchronized GoogleIdTokenVerifier getVerifier() {
        if (this.verifier == null) {
            initVerifier();
        }
        return this.verifier;
    }

    private void initVerifier() {
        GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        ).setIssuers(Arrays.asList("https://accounts.google.com", "accounts.google.com"));

        if (googleClientId != null && !googleClientId.isBlank()) {
            builder.setAudience(Collections.singletonList(googleClientId.trim()));
        }

        this.verifier = builder.build();
    }

    @Override
    public GoogleTokenClaims verify(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new UnauthorizedException("Google ID token must not be null or blank.");
        }

        try {
            GoogleIdToken idToken = getVerifier().verify(idTokenString.trim());
            if (idToken == null) {
                log.warn("[GOOGLE_AUTH] ID token verification failed: signature, audience, or issuer mismatch.");
                throw new UnauthorizedException("Invalid Google ID token. Authentication failed.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            // Verify audience if configured
            if (googleClientId != null && !googleClientId.isBlank()) {
                if (!idToken.verifyAudience(Collections.singletonList(googleClientId.trim()))) {
                    log.warn("[GOOGLE_AUTH] Audience mismatch: expected {}", googleClientId);
                    throw new UnauthorizedException("Google ID token audience mismatch.");
                }
            }

            // Verify email_verified is true
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                log.warn("[GOOGLE_AUTH] Google account email is not verified: {}", payload.getEmail());
                throw new UnauthorizedException("Google email address is not verified by Google.");
            }

            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Google token did not contain an email address.");
            }

            String subject = payload.getSubject();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            return new GoogleTokenClaims(
                    subject,
                    email.toLowerCase().trim(),
                    true,
                    name != null && !name.isBlank() ? name : "Citizen User",
                    picture
            );
        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception ex) {
            log.error("[GOOGLE_AUTH] Error verifying Google token: {}", ex.getMessage());
            throw new UnauthorizedException("Failed to verify Google identity: " + ex.getMessage());
        }
    }
}
