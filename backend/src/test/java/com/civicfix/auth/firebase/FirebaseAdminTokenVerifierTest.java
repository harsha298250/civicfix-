package com.civicfix.auth.firebase;

import com.civicfix.common.exception.UnauthorizedException;
import com.civicfix.config.FirebaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseAdminTokenVerifierTest {

    private FirebaseProperties properties;
    private FirebaseAdminTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        properties = new FirebaseProperties();
        properties.setProjectId("civicfix-production");
        verifier = new FirebaseAdminTokenVerifier(properties);
    }

    @Test
    @DisplayName("Blank or null token throws UnauthorizedException")
    void testBlankTokenThrowsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> verifier.verifyIdToken(null));
        assertThrows(UnauthorizedException.class, () -> verifier.verifyIdToken("   "));
    }

    @Test
    @DisplayName("Uninitialized FirebaseApp throws UnauthorizedException with clear guidance")
    void testUninitializedAppThrowsUnauthorized() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> verifier.verifyIdToken("some.fake.token"));
        assertTrue(ex.getMessage().contains("not configured") || ex.getMessage().contains("Firebase"));
    }
}
