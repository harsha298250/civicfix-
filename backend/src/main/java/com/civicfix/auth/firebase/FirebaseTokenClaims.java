package com.civicfix.auth.firebase;

import java.util.Collections;
import java.util.Map;

public class FirebaseTokenClaims {

    private final String uid;
    private final String email;
    private final boolean emailVerified;
    private final String name;
    private final Map<String, Object> claims;

    public FirebaseTokenClaims(String uid, String email, boolean emailVerified, String name, Map<String, Object> claims) {
        this.uid = uid;
        this.email = email;
        this.emailVerified = emailVerified;
        this.name = name;
        this.claims = claims != null ? Collections.unmodifiableMap(claims) : Collections.emptyMap();
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getClaims() {
        return claims;
    }
}
