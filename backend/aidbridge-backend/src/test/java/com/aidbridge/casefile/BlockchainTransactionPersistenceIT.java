package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.blockchain.adapter.SignedTransaction;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.entity.BlockchainTransaction;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.verification.service.VerificationOutboxService;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * C05 part 3: a signed attempt is persisted (PREPARED) before any broadcast, secrets are stored only as
 * ciphertext, preparation is write-once, and the (chain, signer, nonce) unique index rejects a second
 * attempt with the same nonce. Runs on the real aidbridge_test PostgreSQL (V1..V12).
 */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class BlockchainTransactionPersistenceIT extends AbstractCaseHttpIT {

    private static final String SIGNER = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String REGISTRY = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

    @Autowired VerificationOutboxService outbox;
    @Autowired BlockchainJobRepository jobs;
    @Autowired BlockchainTransactionRepository transactions;
    @Autowired PlatformTransactionManager transactionManager;

    private final JobSecretCipher cipher = new JobSecretCipher(randomBytes(32));

    @org.junit.jupiter.api.BeforeEach
    void retirePriorFixtureAttempts() {
        // Isolated integration-test schema only: keep journal history, retire prior test open attempts.
        jdbc.update("UPDATE blockchain_transactions SET status = 'REVERTED' WHERE chain_id = 31337 AND status IN ('PREPARED','SUBMITTED')");
    }

    @Test
    void persistsPreparedAttemptWithEncryptedBytesBeforeBroadcast() throws Exception {
        UUID jobId = newCaseVerifiedJob();
        byte[] salt = randomBytes(32);
        byte[] commitment = randomBytes(32);
        String rawTx = "0x" + "ab".repeat(120);
        String txHash = "0x" + "cd".repeat(32);

        tx().executeWithoutResult(status -> {
            BlockchainJob job = jobs.findById(jobId).orElseThrow();
            job.attachPreparation("{\"eventType\":\"CASE_VERIFIED\"}",
                    cipher.encrypt(salt, JobSecretCipher.associatedData(jobId, "salt")), commitment);
            job.claim(Instant.now().plusSeconds(60));
            SignedTransaction signed = new SignedTransaction(rawTx, txHash, SIGNER, REGISTRY, BigInteger.ZERO,
                    BigInteger.ONE, BigInteger.valueOf(300_000), 31337);
            transactions.saveAndFlush(BlockchainTransaction.prepared(job, signed,
                    cipher.encrypt(rawTx.getBytes(), JobSecretCipher.associatedData(jobId, "signed-tx")), Instant.now()));
        });

        var row = jdbc.queryForMap("""
                SELECT t.status, t.tx_hash, t.nonce, t.chain_id, t.signer_address, t.contract_address,
                       octet_length(t.signed_tx_ciphertext) AS signed_len, j.status AS job_status,
                       octet_length(j.salt_ciphertext) AS salt_len, octet_length(j.evidence_commitment) AS commitment_len,
                       j.manifest_private::text AS manifest
                FROM blockchain_transactions t JOIN blockchain_jobs j ON j.id = t.blockchain_job_id
                WHERE j.id = ?
                """, jobId);
        assertThat(row).containsEntry("status", "PREPARED")
                .containsEntry("tx_hash", txHash)
                .containsEntry("nonce", 0L)
                .containsEntry("chain_id", 31337L)
                .containsEntry("signer_address", SIGNER)
                .containsEntry("contract_address", REGISTRY)
                .containsEntry("job_status", "PROCESSING")
                .containsEntry("commitment_len", 32);
        // Ciphertexts are longer than the plaintext (version + IV + tag); the raw salt is never stored.
        assertThat((Integer) row.get("salt_len")).isEqualTo(1 + 12 + 32 + 16);
        assertThat((Integer) row.get("signed_len")).isGreaterThan(rawTx.length());
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);

        BlockchainTransaction reloaded = tx().execute(status -> transactions.findByTxHash(txHash).orElseThrow());
        assertThat(new String(cipher.decrypt(reloaded.getSignedTxCiphertext(),
                JobSecretCipher.associatedData(jobId, "signed-tx")))).isEqualTo(rawTx);
    }

    @Test
    void preparationIsWriteOnceAndNonceIsUniquePerSigner() throws Exception {
        UUID jobId = newCaseVerifiedJob();
        tx().executeWithoutResult(status -> {
            BlockchainJob job = jobs.findById(jobId).orElseThrow();
            job.attachPreparation("{}", randomBytes(61), randomBytes(32));
            assertThatThrownBy(() -> job.attachPreparation("{}", randomBytes(61), randomBytes(32)))
                    .hasMessage("JOB_ALREADY_PREPARED");
            job.claim(Instant.now().plusSeconds(60));
            transactions.saveAndFlush(BlockchainTransaction.prepared(job, signedWithNonce(5, "0x" + "01".repeat(32)),
                    randomBytes(40), Instant.now()));
        });

        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            BlockchainJob job = jobs.findById(jobId).orElseThrow();
            transactions.saveAndFlush(BlockchainTransaction.prepared(job, signedWithNonce(5, "0x" + "02".repeat(32)),
                    randomBytes(40), Instant.now()));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
    }

    @Test
    void databaseRejectsTwoOpenAttemptsEvenWithDifferentNoncesOrSigners() throws Exception {
        UUID first = newCaseVerifiedJob();
        UUID second = newCaseVerifiedJob();
        tx().executeWithoutResult(status -> transactions.saveAndFlush(BlockchainTransaction.prepared(
                jobs.findById(first).orElseThrow(), signedWithNonce(200, "0x" + "71".repeat(32)), randomBytes(40), Instant.now())));
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> transactions.saveAndFlush(BlockchainTransaction.prepared(
                jobs.findById(second).orElseThrow(), signedWithNonce(201, "0x" + "72".repeat(32)), randomBytes(40), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        SignedTransaction anotherSigner = new SignedTransaction("0x" + "ab".repeat(100), "0x" + "73".repeat(32),
                "0x" + "33".repeat(20), REGISTRY, BigInteger.valueOf(202), BigInteger.ONE, BigInteger.valueOf(300_000), 31337);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> transactions.saveAndFlush(BlockchainTransaction.prepared(
                jobs.findById(first).orElseThrow(), anotherSigner, randomBytes(40), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("blockchain_transactions", "blockchain_job_id", first)).isEqualTo(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", second)).isZero();
    }

    private UUID newCaseVerifiedJob() throws Exception {
        var created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        long version = created.path("version").asLong();
        UUID verificationId = UUID.randomUUID();
        UUID auditEventId = tx().execute(status -> {
            jdbc.update("""
                    INSERT INTO verifications (id, aid_case_id, verifier_id, decision, reviewed_case_version)
                    VALUES (?, ?, ?, 'APPROVED', ?)
                    """, verificationId, caseId, verifier.id(), version);
            return outbox.enqueueApprovedVerification(ORG_A, verificationId);
        });
        return jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, auditEventId);
    }

    private static SignedTransaction signedWithNonce(long nonce, String hash) {
        return new SignedTransaction("0x" + "ab".repeat(100), hash, SIGNER, REGISTRY, BigInteger.valueOf(nonce),
                BigInteger.ONE, BigInteger.valueOf(300_000), 31337);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }
}
