package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.BlockchainRpcException;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CanonicalJson;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.envelope.EvidenceCommitments;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.blockchain.worker.CaseVerifiedManifestBuilder;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import com.aidbridge.verification.service.VerificationOutboxService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

/**
 * C05 part 4 on real PostgreSQL with a scripted node: a CASE_VERIFIED job from B05 is prepared once,
 * persisted as PREPARED before broadcast, broadcast with calldata matching the DB envelope, and marked
 * SUBMITTED. Unbound organizations, other chains and broadcast failures never lose the persisted bytes.
 */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class CaseVerifiedWorkerIT extends AbstractCaseHttpIT {

    // Public Hardhat development account #0 (documented test key, never funded on a real network).
    private static final String HARDHAT_KEY_0 = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String HARDHAT_ADDRESS_0 = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String REGISTRY = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

    @Autowired VerificationOutboxService outbox;
    @Autowired BlockchainJobRepository jobs;
    @Autowired BlockchainTransactionRepository attempts;
    @Autowired PlatformTransactionManager transactionManager;

    private final BlockchainProperties properties =
            new BlockchainProperties(true, "http://127.0.0.1:8545", 31337, REGISTRY, HARDHAT_KEY_0, null, null);
    private final JobSecretCipher cipher = new JobSecretCipher(randomBytes(32));
    private FakeBlockchainAdapter node;
    private CaseVerifiedWorker worker;

    @BeforeEach
    void wireWorker() {
        node = new FakeBlockchainAdapter(properties);
        // A real node remembers every transaction this signer already sent on the chain (other tests share this
        // schema), so the fake starts from that nonce instead of 0.
        node.pendingNonce = java.math.BigInteger.valueOf(jdbc.queryForObject(
                "SELECT COALESCE(MAX(nonce) + 1, 0) FROM blockchain_transactions WHERE chain_id = 31337 AND LOWER(signer_address) = ?",
                Long.class, HARDHAT_ADDRESS_0));
        worker = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC());
        // Leave no due local-chain job from an earlier test in this schema.
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = now() + interval '1 day' WHERE chain_id = 31337 AND status IN ('PENDING','PROCESSING','SUBMITTED')");
        jdbc.update("UPDATE organizations SET relayer_address = ? WHERE id = ?", HARDHAT_ADDRESS_0, ORG_A);
    }

    @Test
    void preparesPersistsBroadcastsAndSubmitsWithCalldataMatchingTheDatabase() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        // The node's pending nonce is set above every nonce already used in this shared schema, so the
        // worker must take exactly the node's value (independent of test execution order).
        long nodeNonce = jdbc.queryForObject(
                "SELECT COALESCE(MAX(nonce), 0) FROM blockchain_transactions WHERE chain_id = 31337", Long.class) + 1000;
        node.pendingNonce = java.math.BigInteger.valueOf(nodeNonce);

        var result = worker.processNextJob().orElseThrow();

        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.outcome()).isEqualTo(CaseVerifiedWorker.Outcome.SUBMITTED);
        assertThat(result.reasonCode()).isNull();
        assertThat(node.broadcasts).hasSize(1);

        var row = jdbc.queryForMap("""
                SELECT j.status, j.manifest_private::text AS manifest, j.salt_ciphertext, j.evidence_commitment,
                       j.logical_event_id, j.chain_sequence, o.public_ref AS org_ref, c.public_ref AS case_ref,
                       t.status AS tx_status, t.nonce, t.signer_address, t.contract_address, t.signed_tx_ciphertext, t.tx_hash
                FROM blockchain_jobs j
                JOIN audit_events a ON a.id = j.audit_event_id
                JOIN organizations o ON o.id = a.organization_id
                JOIN aid_cases c ON c.id = a.aid_case_id
                JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                WHERE j.id = ?
                """, jobId);
        assertThat(row).containsEntry("status", "SUBMITTED").containsEntry("tx_status", "SUBMITTED")
                .containsEntry("nonce", nodeNonce).containsEntry("signer_address", HARDHAT_ADDRESS_0)
                .containsEntry("contract_address", REGISTRY);

        // The broadcast bytes are exactly the persisted (encrypted) bytes.
        String broadcast = node.broadcasts.get(0);
        byte[] persisted = cipher.decrypt((byte[]) row.get("signed_tx_ciphertext"), JobSecretCipher.associatedData(jobId, "signed-tx"));
        assertThat(Numeric.toHexString(persisted)).isEqualTo(broadcast);

        // The commitment is reproducible from the stored manifest and the decrypted salt (jsonb is re-canonicalized).
        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = json.readValue((String) row.get("manifest"), Map.class);
        assertThat(manifest).containsOnlyKeys("actorRef", "caseRef", "eventId", "eventType", "evidenceVersions",
                "goodsLines", "occurredAt", "orgRef", "reviewedCaseVersion", "schemaVersion", "verificationId");
        byte[] salt = cipher.decrypt((byte[]) row.get("salt_ciphertext"), JobSecretCipher.associatedData(jobId, "salt"));
        byte[] expectedCommitment = EvidenceCommitments.evidenceCommitment(31337, REGISTRY, (byte[]) row.get("case_ref"),
                (byte[]) row.get("logical_event_id"), EvidenceCommitments.manifestDigest(CanonicalJson.toCanonicalUtf8(manifest)), salt);
        assertThat((byte[]) row.get("evidence_commitment")).isEqualTo(expectedCommitment);

        // Calldata == recordCaseVerification(orgRef, caseRef, eventId, sequence, commitment, schemaVersion) from the DB.
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(broadcast);
        String expectedCalldata = AidRegistryAbi.encodeRecordCaseVerification(new CaseVerifiedEnvelope(
                (byte[]) row.get("org_ref"), (byte[]) row.get("case_ref"), (byte[]) row.get("logical_event_id"),
                (Long) row.get("chain_sequence"), expectedCommitment, 1));
        assertThat(Numeric.prependHexPrefix(decoded.getData())).isEqualTo(expectedCalldata);
        assertThat(decoded.getChainId()).isEqualTo(31337L);
        assertThat(decoded.getFrom().toLowerCase()).isEqualTo(HARDHAT_ADDRESS_0);
        assertThat(row.get("tx_hash")).isEqualTo(org.web3j.crypto.Hash.sha3(broadcast));
    }

    @Test
    void skipsUnboundOrganizationAndOtherChainsWithoutPreparing() throws Exception {
        UUID fujiJob = newCaseVerifiedJob(); // chain_id 43113 by default: never sent by a local worker
        UUID localJob = newLocalCaseVerifiedJob();
        jdbc.update("UPDATE organizations SET relayer_address = NULL WHERE id = ?", ORG_A);

        var result = worker.processNextJob().orElseThrow();

        assertThat(result.jobId()).isEqualTo(localJob);
        assertThat(result.outcome()).isEqualTo(CaseVerifiedWorker.Outcome.BINDING_MISSING);
        assertThat(node.broadcasts).isEmpty();
        assertThat(jdbc.queryForMap("SELECT status, last_error, evidence_commitment FROM blockchain_jobs WHERE id = ?", localJob))
                .containsEntry("status", "PENDING").containsEntry("last_error", "ORGANIZATION_RELAYER_NOT_BOUND")
                .containsEntry("evidence_commitment", null);
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, fujiJob)).isEqualTo("PENDING");
        assertThat(worker.processNextJob()).isEmpty(); // retry is scheduled in the future
    }

    @Test
    void unknownBroadcastOutcomeKeepsTheSameBytesAndARebroadcastReusesThem() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        node.nextBroadcastFailure = new BlockchainRpcException("RPC_UNAVAILABLE", "simulated timeout");

        var first = worker.processNextJob().orElseThrow();
        assertThat(first.reasonCode()).isEqualTo("RPC_UNAVAILABLE");
        String hash = jdbc.queryForObject("SELECT tx_hash FROM blockchain_transactions WHERE blockchain_job_id = ?", String.class, jobId);
        assertThat(jdbc.queryForObject("SELECT error_message FROM blockchain_transactions WHERE blockchain_job_id = ?", String.class, jobId))
                .isEqualTo("RPC_UNAVAILABLE");

        // Simulate a crash before TX3: the attempt is back to PREPARED and the job's lease has expired.
        jdbc.update("UPDATE blockchain_transactions SET status = 'PREPARED' WHERE blockchain_job_id = ?", jobId);
        jdbc.update("UPDATE blockchain_jobs SET status = 'PROCESSING', lease_until = now() - interval '1 second' WHERE id = ?", jobId);
        byte[] commitmentBefore = jdbc.queryForObject("SELECT evidence_commitment FROM blockchain_jobs WHERE id = ?", byte[].class, jobId);

        var second = worker.processNextJob().orElseThrow();

        assertThat(second.outcome()).isEqualTo(CaseVerifiedWorker.Outcome.REBROADCAST);
        assertThat(node.broadcasts).hasSize(2);
        assertThat(node.broadcasts.get(1)).isEqualTo(node.broadcasts.get(0));
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tx_hash FROM blockchain_transactions WHERE blockchain_job_id = ?", String.class, jobId)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT evidence_commitment FROM blockchain_jobs WHERE id = ?", byte[].class, jobId)).isEqualTo(commitmentBefore);
    }

    @Test
    void confirmsOnlyAfterTheExactReceiptAndStoresTheBlock() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow())
                .as("the job must be sent before its receipt can be checked")
                .extracting(CaseVerifiedWorker.Result::jobId, CaseVerifiedWorker.Result::outcome, CaseVerifiedWorker.Result::reasonCode)
                .containsExactly(jobId, CaseVerifiedWorker.Outcome.SUBMITTED, null);
        node.mine(node.broadcasts.get(0), REGISTRY, 100, null);

        worker.pollSubmitted();

        var row = jdbc.queryForMap("""
                SELECT j.status, j.last_error, j.lease_until, t.status AS tx_status, t.block_number, t.receipt_status,
                       t.confirmed_at, t.receipt_private::text AS receipt
                FROM blockchain_jobs j JOIN blockchain_transactions t ON t.blockchain_job_id = j.id WHERE j.id = ?
                """, jobId);
        assertThat(row).containsEntry("status", "CONFIRMED").containsEntry("tx_status", "CONFIRMED")
                .containsEntry("block_number", 100L).containsEntry("last_error", null).containsEntry("lease_until", null);
        assertThat(((Number) row.get("receipt_status")).intValue()).isEqualTo(1);
        assertThat(row.get("confirmed_at")).isNotNull();
        assertThat((String) row.get("receipt")).contains("\"blockNumber\": 100").contains("\"status\": 1");
    }

    @Test
    void aMismatchedLogFailsTheJobAndIsNeverConfirmed() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow())
                .as("the job must be sent before its receipt can be checked")
                .extracting(CaseVerifiedWorker.Result::jobId, CaseVerifiedWorker.Result::outcome, CaseVerifiedWorker.Result::reasonCode)
                .containsExactly(jobId, CaseVerifiedWorker.Outcome.SUBMITTED, null);
        node.mine(node.broadcasts.get(0), REGISTRY, 101, "0x" + "ee".repeat(32));

        worker.pollSubmitted();

        assertThat(jdbc.queryForMap("""
                SELECT j.status, j.last_error, t.status AS tx_status, t.error_message, t.confirmed_at
                FROM blockchain_jobs j JOIN blockchain_transactions t ON t.blockchain_job_id = j.id WHERE j.id = ?
                """, jobId))
                .containsEntry("status", "FAILED").containsEntry("last_error", "RECEIPT_EVENT_ID_MISMATCH")
                .containsEntry("tx_status", "SUBMITTED").containsEntry("error_message", "RECEIPT_EVENT_ID_MISMATCH")
                .containsEntry("confirmed_at", null);
    }

    @Test
    void waitsWhileNotMinedThenRebroadcastsTheSamePersistedBytes() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow())
                .as("the job must be sent before its receipt can be checked")
                .extracting(CaseVerifiedWorker.Result::jobId, CaseVerifiedWorker.Result::outcome, CaseVerifiedWorker.Result::reasonCode)
                .containsExactly(jobId, CaseVerifiedWorker.Outcome.SUBMITTED, null);

        var pending = worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();
        assertThat(pending.outcome()).isEqualTo(CaseVerifiedWorker.PollOutcome.PENDING);
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, jobId)).isEqualTo("SUBMITTED");

        // Three minutes later, still no receipt: the same bytes are re-sent, nothing is re-signed.
        jdbc.update("UPDATE blockchain_transactions SET submitted_at = now() - interval '3 minutes' WHERE blockchain_job_id = ?", jobId);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        var rebroadcast = worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();

        assertThat(rebroadcast.outcome()).isEqualTo(CaseVerifiedWorker.PollOutcome.REBROADCAST);
        assertThat(node.broadcasts).hasSize(2);
        assertThat(node.broadcasts.get(1)).isEqualTo(node.broadcasts.get(0));
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, jobId)).isEqualTo("SUBMITTED");
    }

    @Test
    void exhaustedRecoveryBudgetKeepsOriginalAttemptAndAcceptsLateReceiptAfterRestart() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        worker.processNextJob().orElseThrow();
        String raw = node.broadcasts.get(0);
        var before = jdbc.queryForMap("SELECT tx_hash, nonce, signed_tx_ciphertext FROM blockchain_transactions WHERE blockchain_job_id = ?", jobId);
        jdbc.update("UPDATE blockchain_transactions SET submitted_at = now() - interval '10 minutes' WHERE blockchain_job_id = ?", jobId);
        jdbc.update("UPDATE blockchain_jobs SET retry_count = 10, next_retry_at = NULL WHERE id = ?", jobId);
        for (int i = 0; i < 3; i++) {
            jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
            var pending = worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();
            assertThat(pending.outcome()).isEqualTo(CaseVerifiedWorker.PollOutcome.PENDING);
            assertThat(pending.reasonCode()).isEqualTo("RECEIPT_PENDING_OPERATOR_ATTENTION");
        }
        assertThat(node.broadcasts).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, jobId)).isEqualTo("SUBMITTED");
        var after = jdbc.queryForMap("SELECT tx_hash, nonce, signed_tx_ciphertext FROM blockchain_transactions WHERE blockchain_job_id = ?", jobId);
        assertThat(after.get("tx_hash")).isEqualTo(before.get("tx_hash"));
        assertThat(after.get("nonce")).isEqualTo(before.get("nonce"));
        assertThat((byte[]) after.get("signed_tx_ciphertext")).containsExactly((byte[]) before.get("signed_tx_ciphertext"));
        worker = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC());
        node.mine(raw, REGISTRY, 102, null);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().outcome())
                .isEqualTo(CaseVerifiedWorker.PollOutcome.CONFIRMED);
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
    }

    @Test
    void repeatedRpcOutageAndDuplicateBroadcastNeverTurnUnknownOutcomeIntoFailure() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        worker.processNextJob().orElseThrow();
        String raw = node.broadcasts.get(0);
        jdbc.update("UPDATE blockchain_transactions SET submitted_at = now() - interval '10 minutes' WHERE blockchain_job_id = ?", jobId);
        node.nextBroadcastFailure = new BlockchainRpcException("RPC_REJECTED", "already known");
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().outcome())
                .isEqualTo(CaseVerifiedWorker.PollOutcome.REBROADCAST);
        assertThat(node.broadcasts.get(1)).isEqualTo(raw);
        for (int i = 0; i < 6; i++) {
            node.nextReceiptFailure = new BlockchainRpcException("RPC_UNAVAILABLE", "outage");
            jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
            assertThat(worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().outcome())
                    .isEqualTo(CaseVerifiedWorker.PollOutcome.PENDING);
        }
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().outcome())
                .isEqualTo(CaseVerifiedWorker.PollOutcome.PENDING);
        node.mine(raw, REGISTRY, 103, null);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow().outcome())
                .isEqualTo(CaseVerifiedWorker.PollOutcome.CONFIRMED);
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
    }

    @Test
    void concurrentWorkersClaimOneJobAndPersistOnlyOneAttempt() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        var second = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return worker.processNextJob(); });
            var b = pool.submit(() -> { start.await(); return second.processNextJob(); });
            start.countDown();
            var firstResult = a.get(15, java.util.concurrent.TimeUnit.SECONDS);
            var secondResult = b.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat((firstResult.isPresent() ? 1 : 0) + (secondResult.isPresent() ? 1 : 0)).isEqualTo(1);
            assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
            assertThat(node.broadcasts).hasSize(1);
        } finally { pool.shutdownNow(); }
    }

    private UUID newLocalCaseVerifiedJob() throws Exception {
        UUID jobId = newCaseVerifiedJob();
        // B05 creates Fuji jobs (43113); this local run targets the Hardhat chain only.
        jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE id = ?", jobId);
        return jobId;
    }

    private UUID newCaseVerifiedJob() throws Exception {
        var created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        long version = created.path("version").asLong();
        UUID verificationId = UUID.randomUUID();
        UUID auditEventId = new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("""
                    INSERT INTO verifications (id, aid_case_id, verifier_id, decision, reviewed_case_version)
                    VALUES (?, ?, ?, 'APPROVED', ?)
                    """, verificationId, caseId, verifier.id(), version);
            return outbox.enqueueApprovedVerification(ORG_A, verificationId);
        });
        return jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, auditEventId);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
