package com.civicfix.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AesGcmEncryptionHelper
 *
 * Provides authenticated symmetric encryption (AES-256-GCM) for sensitive tokens
 * (e.g. password reset tokens, verification tokens) stored at rest in the PostgreSQL email outbox.
 *
 * Security specifications:
 * - Cipher: AES/GCM/NoPadding
 * - Key length: 256 bits (32 bytes)
 * - IV: 96-bit (12 bytes) cryptographically random nonce generated per encryption
 * - Tag length: 128 bits (16 bytes)
 * - Format: Base64([12-byte IV][ciphertext + 16-byte GCM tag])
 */
@Component
public class AesGcmEncryptionHelper {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptionHelper.class);
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionHelper() {
        this("");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AesGcmEncryptionHelper(
            @Value("${civicfix.outbox.encryption-key:${CIVICFIX_OUTBOX_ENCRYPTION_KEY:}}") String hexKey) {
        if (hexKey != null && !hexKey.trim().isEmpty() && hexKey.trim().length() == 64) {
            byte[] keyBytes = HexFormat.of().parseHex(hexKey.trim());
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            log.info("[OUTBOX_ENCRYPTION] Initialized with production 256-bit AES-GCM key.");
        } else {
            // Development fallback: derive reproducible 256-bit key with explicit warning
            log.warn("[OUTBOX_ENCRYPTION] CIVICFIX_OUTBOX_ENCRYPTION_KEY is not set or not 64 hex characters. Using default development fallback key.");
            byte[] fallbackBytes = "CIVICFIX_DEV_OUTBOX_AES_KEY_2026".getBytes(StandardCharsets.UTF_8); // Exactly 32 bytes (256 bits)
            this.secretKey = new SecretKeySpec(fallbackBytes, ALGORITHM);
        }
    }

    /**
     * Testing constructor allowing custom SecretKey injection.
     */
    public AesGcmEncryptionHelper(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Encrypts plaintext string using AES-256-GCM with a fresh random IV.
     *
     * @param plaintext Sensitive data to encrypt
     * @return Base64-encoded string containing [IV (12 bytes) + Ciphertext + Tag]
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherTextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherTextWithTag);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception ex) {
            log.error("[OUTBOX_ENCRYPTION] Encryption failed: {}", ex.getMessage());
            throw new IllegalStateException("Failed to encrypt sensitive outbox data", ex);
        }
    }

    /**
     * Decrypts Base64-encoded AES-256-GCM ciphertext.
     *
     * @param base64Ciphertext Base64 string containing [IV (12 bytes) + Ciphertext + Tag]
     * @return Decrypted UTF-8 plaintext string
     */
    public String decrypt(String base64Ciphertext) {
        if (base64Ciphertext == null || base64Ciphertext.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Ciphertext.trim());
            if (decoded.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Ciphertext buffer too short for AES-GCM verification");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherTextWithTag = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextWithTag);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(cipherTextWithTag);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("[OUTBOX_ENCRYPTION] Decryption/verification failed: {}", ex.getMessage());
            throw new IllegalStateException("Failed to decrypt or authenticate outbox data", ex);
        }
    }
}
