package com.civicfix.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEncryptionHelperTest {

    private AesGcmEncryptionHelper helper;

    @BeforeEach
    void setUp() {
        // Use a test 64-hex char key (32 bytes)
        String testHexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        helper = new AesGcmEncryptionHelper(testHexKey);
    }

    @Test
    @DisplayName("Encrypt and decrypt roundtrip restores original plaintext")
    void testEncryptDecryptRoundtrip() {
        String original = "https://civicfix.org/reset-password?token=secret_token_123456789";
        String encrypted = helper.encrypt(original);

        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = helper.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Subsequent encryptions of same plaintext produce distinct ciphertexts due to fresh random IV")
    void testFreshIvPerEncryption() {
        String plaintext = "SuperSecretResetToken";
        String enc1 = helper.encrypt(plaintext);
        String enc2 = helper.encrypt(plaintext);

        assertNotNull(enc1);
        assertNotNull(enc2);
        assertNotEquals(enc1, enc2, "Each encryption must use a fresh random IV");

        assertEquals(plaintext, helper.decrypt(enc1));
        assertEquals(plaintext, helper.decrypt(enc2));
    }

    @Test
    @DisplayName("Decryption fails when ciphertext is tampered or corrupted")
    void testTamperedCiphertextThrows() {
        String plaintext = "ImportantTokenPayload";
        String encrypted = helper.encrypt(plaintext);

        // Tamper with base64 ciphertext
        char lastChar = encrypted.charAt(encrypted.length() - 2);
        char replaced = lastChar == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 2) + replaced + encrypted.substring(encrypted.length() - 1);

        assertThrows(IllegalStateException.class, () -> helper.decrypt(tampered),
                "GCM authentication tag verification must fail on tampered ciphertext");
    }

    @Test
    @DisplayName("Null or empty handling returns null gracefully")
    void testNullHandling() {
        assertNull(helper.encrypt(null));
        assertNull(helper.decrypt(null));
        assertNull(helper.decrypt("   "));
    }
}
