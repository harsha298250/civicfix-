package com.civicfix.auth.google;

public interface GoogleTokenVerifier {

    /**
     * Verifies a Google OAuth ID token issued by Google Identity Services.
     *
     * @param idToken the raw JWT string issued by Google Identity Services
     * @return verified claims extracted directly from the signed Google ID token
     * @throws com.civicfix.common.exception.UnauthorizedException if invalid, expired, or untrusted
     */
    GoogleTokenClaims verify(String idToken);
}
