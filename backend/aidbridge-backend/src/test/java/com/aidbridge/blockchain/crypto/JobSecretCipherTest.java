package com.aidbridge.blockchain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JobSecretCipherTest {

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private final JobSecretCipher cipher = new JobSecretCipher(randomKey());
    private final UUID jobId = UUID.randomUUID();
    private final byte[] salt = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void roundTripsAndUsesAFreshIvEachTime() {
        byte[] aad = JobSecretCipher.associatedData(jobId, "salt");
        byte[] first = cipher.encrypt(salt, aad);
        byte[] second = cipher.encrypt(salt, aad);

        assertThat(cipher.decrypt(first, aad)).isEqualTo(salt);
        assertThat(first).isNotEqualTo(second);
        assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain(new String(salt, StandardCharsets.ISO_8859_1));
    }

    @Test
    void rejectsTamperingOtherJobOtherPurposeAndOtherKey() {
        byte[] sealed = cipher.encrypt(salt, JobSecretCipher.associatedData(jobId, "salt"));

        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThatThrownBy(() -> cipher.decrypt(tampered, JobSecretCipher.associatedData(jobId, "salt")))
                .hasMessage("CIPHERTEXT_REJECTED");
        assertThatThrownBy(() -> cipher.decrypt(sealed, JobSecretCipher.associatedData(UUID.randomUUID(), "salt")))
                .hasMessage("CIPHERTEXT_REJECTED");
        assertThatThrownBy(() -> cipher.decrypt(sealed, JobSecretCipher.associatedData(jobId, "signed-tx")))
                .hasMessage("CIPHERTEXT_REJECTED");
        assertThatThrownBy(() -> new JobSecretCipher(randomKey()).decrypt(sealed, JobSecretCipher.associatedData(jobId, "salt")))
                .hasMessage("CIPHERTEXT_REJECTED");
    }

    @Test
    void validatesKeyAndNeverPrintsIt() {
        byte[] key = randomKey();
        String base64 = Base64.getEncoder().encodeToString(key);
        assertThat(JobSecretCipher.fromBase64(base64).toString()).isEqualTo("JobSecretCipher[key=<redacted>]");

        assertThatThrownBy(() -> JobSecretCipher.fromBase64("")).hasMessageStartingWith("MISSING_OR_INVALID_SECRET");
        assertThatThrownBy(() -> JobSecretCipher.fromBase64("not base64!")).hasMessageStartingWith("MISSING_OR_INVALID_SECRET");
        assertThatThrownBy(() -> new JobSecretCipher(new byte[16])).hasMessageStartingWith("MISSING_OR_INVALID_SECRET");
    }
}
