package com.civicfix.auth.firebase;

public interface FirebaseTokenVerifier {
    /**
     * Verifies a Firebase ID token.
     *
     * @param idToken the raw JWT string issued by Firebase Authentication
     * @return parsed and validated token claims
     * @throws com.civicfix.common.exception.UnauthorizedException if the token is invalid, expired, revoked, or audience mismatch
     */
    FirebaseTokenClaims verifyIdToken(String idToken);
}
