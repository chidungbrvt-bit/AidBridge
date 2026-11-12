package com.aidbridge.blockchain.crypto;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for job secrets kept off-chain (D48): the commitment salt ({@code salt_ciphertext}) and the
 * exact signed transaction bytes ({@code signed_tx_ciphertext}). Layout: {@code version(1) || iv(12) ||
 * ciphertext+tag}. Associated data binds a ciphertext to its job and purpose, so it cannot be swapped
 * between rows. The key comes from {@code BLOCKCHAIN_SECRET_KEY} (base64, 32 bytes) and is never printed.
 */
public final class JobSecretCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public JobSecretCipher(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalStateException("MISSING_OR_INVALID_SECRET: job secret key must be 32 bytes");
        }
        this.key = new SecretKeySpec(rawKey.clone(), "AES");
    }

    public static JobSecretCipher fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("MISSING_OR_INVALID_SECRET: BLOCKCHAIN_SECRET_KEY is required");
        }
        try {
            return new JobSecretCipher(Base64.getDecoder().decode(base64Key.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MISSING_OR_INVALID_SECRET: BLOCKCHAIN_SECRET_KEY must be base64");
        }
    }

    public byte[] encrypt(byte[] plaintext, byte[] associatedData) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData);
            byte[] sealed = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(1 + IV_BYTES + sealed.length).put(FORMAT_VERSION).put(iv).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ENCRYPTION_FAILED", e);
        }
    }

    public byte[] decrypt(byte[] envelope, byte[] associatedData) {
        if (envelope == null || envelope.length < 1 + IV_BYTES + TAG_BITS / 8 || envelope[0] != FORMAT_VERSION) {
            throw new IllegalStateException("CIPHERTEXT_REJECTED");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, envelope, 1, IV_BYTES));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(envelope, 1 + IV_BYTES, envelope.length - 1 - IV_BYTES);
        } catch (GeneralSecurityException e) {
            // Wrong key, wrong job/purpose binding or tampered bytes: never return partial data.
            throw new IllegalStateException("CIPHERTEXT_REJECTED");
        }
    }

    /** Associated data that binds a secret to one job and one purpose (e.g. "salt", "signed-tx"). */
    public static byte[] associatedData(java.util.UUID jobId, String purpose) {
        return ("aidbridge:blockchain-job:" + jobId + ":" + purpose).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JobSecretCipher[key=<redacted>]";
    }
}
