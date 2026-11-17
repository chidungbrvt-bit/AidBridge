package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.BlockchainRpcException;
import com.aidbridge.blockchain.adapter.ChainReceipt;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CanonicalJson;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.envelope.EvidenceCommitments;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.blockchain.worker.CaseVerifiedManifestBuilder;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import com.aidbridge.blockchain.worker.SignerSessionLock;
import com.aidbridge.verification.service.VerificationOutboxService;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.Hash;
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
class CaseVerifiedWorkerIT extends AbstractCampaignHttpIT {

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
        // Test-only retirement of prior test attempts in this isolated schema; not an operational cleanup.
        jdbc.update("UPDATE blockchain_transactions SET status = 'REVERTED' WHERE chain_id = 31337 AND status IN ('PREPARED','SUBMITTED')");
        node = new FakeBlockchainAdapter(properties);
        // A real node remembers every transaction this signer already sent on the chain (other tests share this
        // schema), so the fake starts from that nonce instead of 0.
        node.pendingNonce = java.math.BigInteger.valueOf(jdbc.queryForObject(
                "SELECT COALESCE(MAX(nonce) + 1, 0) FROM blockchain_transactions WHERE chain_id = 31337 AND LOWER(signer_address) = ?",
                Long.class, HARDHAT_ADDRESS_0));
        worker = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC(), new SignerSessionLock(jdbc.getDataSource()));
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
                SELECT j.status, j.last_error, j.lease_until, t.status AS tx_status,
                       t.block_number, t.receipt_status, t.confirmed_at,
                       CAST(t.receipt_private AS text) AS receipt,
                       j.logical_event_id, j.audit_event_id, j.aid_case_id AS case_id,
                       o.public_ref AS org_ref, c.public_ref AS case_ref
                FROM blockchain_jobs j
                JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                JOIN aid_cases c ON c.id = j.aid_case_id
                JOIN organizations o ON o.id = c.organization_id
                WHERE j.id = ?
                """, jobId);
        assertThat(row).containsEntry("status", "CONFIRMED").containsEntry("tx_status", "CONFIRMED")
                .containsEntry("block_number", 100L).containsEntry("last_error", null).containsEntry("lease_until", null);
        assertThat(((Number) row.get("receipt_status")).intValue()).isEqualTo(1);
        assertThat(row.get("confirmed_at")).isNotNull();

        var receipt = json.readTree((String) row.get("receipt"));
        assertThat(receipt.path("blockNumber").asLong()).isEqualTo(100L);
        assertThat(receipt.path("status").asInt()).isEqualTo(1);
        assertThat(receipt.path("contractAddress").asText())
                .as("receipt_private.contractAddress").isEqualTo(REGISTRY);
        assertThat(receipt.path("eventId").asText()).as("receipt_private.eventId")
                .isEqualTo(Numeric.toHexString((byte[]) row.get("logical_event_id")));
        assertThat(receipt.path("chainSequence").isIntegralNumber())
                .as("receipt_private.chainSequence is an integer").isTrue();
        assertThat(receipt.path("chainSequence").asLong())
                .as("receipt_private.chainSequence").isEqualTo(1L);
        assertThat(receipt.path("eventType").asText())
                .as("receipt_private.eventType").isEqualTo("CASE_VERIFIED");
        assertThat(receipt.path("schemaVersion").isIntegralNumber())
                .as("receipt_private.schemaVersion is an integer").isTrue();
        assertThat(receipt.path("schemaVersion").asInt())
                .as("receipt_private.schemaVersion").isEqualTo(1);
        assertThat(receipt.path("orgRef").asText()).as("receipt_private.orgRef")
                .isEqualTo(Numeric.toHexString((byte[]) row.get("org_ref")));
        assertThat(receipt.path("caseRef").asText()).as("receipt_private.caseRef")
                .isEqualTo(Numeric.toHexString((byte[]) row.get("case_ref")));

        var trail = volunteer.browser().send(
                "GET", "/api/cases/" + row.get("case_id") + "/audit-trail", null, null, false);
        status(trail, 200);
        var event = StreamSupport.stream(node(trail).path("items").spliterator(), false)
                .filter(item -> row.get("audit_event_id").toString().equals(item.path("eventId").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(event.path("logicalEventId").asText()).isEqualTo(receipt.path("eventId").asText());
        assertThat(event.path("blockchain").path("status").asText()).isEqualTo("CONFIRMED_ON_CHAIN");
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

    @ParameterizedTest
    @CsvSource({
        "WRONG_CONTRACT, SUBMITTED, RECEIPT_WRONG_CONTRACT",
        "MISSING_EVENT, SUBMITTED, RECEIPT_EVENT_MISSING",
        "REVERTED, REVERTED, TX_REVERTED",
        "FOREIGN_REVERT, SUBMITTED, RECEIPT_WRONG_CONTRACT"
    })
    void rejectsReceiptWithoutMatchingProof(String fixture, String attemptStatus, String reason)
            throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(jobId);
        var before = receiptState(jobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 104, null);
        String hash = (String) before.get("tx_hash");
        ChainReceipt valid = node.receipts.get(hash);
        String destination = fixture.equals("WRONG_CONTRACT") || fixture.equals("FOREIGN_REVERT")
                ? "0x" + "99".repeat(20) : valid.to();
        int status = fixture.equals("REVERTED") || fixture.equals("FOREIGN_REVERT") ? 0 : 1;
        var logs = fixture.equals("MISSING_EVENT") || status == 0 ? List.<ChainReceipt.ChainLog>of() : valid.logs();
        node.receipts.put(hash, new ChainReceipt(hash, status, valid.blockNumber(), valid.blockHash(),
                valid.from(), destination, logs));

        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.FAILED, reason));
        assertThat(receiptState(jobId)).containsEntry("job_status", "FAILED").containsEntry("attempt_status", attemptStatus)
                .containsEntry("last_error", reason).containsEntry("error_message", reason)
                .containsEntry("receipt_status", fixture.equals("REVERTED") ? 0 : null)
                .containsEntry("confirmed_at", null).containsEntry("attempt_id", before.get("attempt_id"))
                .containsEntry("tx_hash", hash).containsEntry("nonce", before.get("nonce"));
        assertThat(node.broadcasts).hasSize(1);
    }

    @Test
    void defersOnWrongRpcChainAndNoncanonicalBlockBeforeConfirmingOriginalAttempt() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(jobId);
        var before = receiptState(jobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 105, null);
        node.chainId = 43113;
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.PENDING, "INVALID_CHAIN_ID"));
        assertThat(receiptState(jobId)).containsEntry("job_status", "SUBMITTED")
                .containsEntry("attempt_status", "SUBMITTED").containsEntry("confirmed_at", null);

        node.chainId = 31337;
        var block = java.math.BigInteger.valueOf(105);
        String canonicalHash = node.canonicalBlocks.put(block, "0x" + "ee".repeat(32));
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                jobId, CaseVerifiedWorker.PollOutcome.PENDING, "BLOCK_NOT_CANONICAL"));
        assertThat(receiptState(jobId)).containsEntry("job_status", "SUBMITTED")
                .containsEntry("attempt_status", "SUBMITTED").containsEntry("confirmed_at", null);

        node.canonicalBlocks.put(block, canonicalHash);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        assertThat(receiptState(jobId)).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED")
                .containsEntry("attempt_id", before.get("attempt_id")).containsEntry("tx_hash", before.get("tx_hash"))
                .containsEntry("nonce", before.get("nonce"));
        assertThat(node.broadcasts).hasSize(1);
    }

    @Test
    void receiptTimeoutKeepsApprovalPendingAndLateProofConfirmsTheOriginalAttempt() throws Exception {
        var submitted = create();
        UUID caseId = UUID.fromString(submitted.path("id").asText());
        var evidenceIds = new ArrayList<String>();
        submitted.path("evidenceVersions").forEach(version -> evidenceIds.add(version.path("id").asText()));
        var request = Map.of("expectedVersion", submitted.path("version").asLong(), "reviewedEvidenceVersionIds", evidenceIds);
        String path = "/api/cases/" + caseId + "/verify";
        String key = UUID.randomUUID().toString();
        var approved = verifier.browser().send("POST", path, request, key, true);
        status(approved, 200);
        var approval = node(approved);
        assertThat(approval.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");
        UUID auditId = UUID.fromString(approval.path("auditEventId").asText());
        UUID jobId = jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, auditId);
        jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE id = ?", jobId);
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(jobId);
        var before = receiptState(jobId);

        node.mine(node.broadcasts.get(0), REGISTRY, 106, null);
        node.nextReceiptFailure = new BlockchainRpcException("RPC_UNAVAILABLE", "Simulated receipt timeout");
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.PENDING, "RPC_UNAVAILABLE"));
        assertThat(receiptState(jobId)).containsEntry("job_status", "SUBMITTED").containsEntry("attempt_status", "SUBMITTED")
                .containsEntry("last_error", "RPC_UNAVAILABLE").containsEntry("receipt_status", null)
                .containsEntry("confirmed_at", null);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId)).isEqualTo("VERIFIED");
        var replay = verifier.browser().send("POST", path, request, key, true);
        status(replay, 200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(node(replay).path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");

        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        assertThat(receiptState(jobId)).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED")
                .containsEntry("attempt_id", before.get("attempt_id")).containsEntry("tx_hash", before.get("tx_hash"))
                .containsEntry("nonce", before.get("nonce")).containsEntry("receipt_status", 1);
        assertThat(count("verifications", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_VERIFIED'",
                Long.class, caseId)).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", auditId)).isEqualTo(1);
        assertThat(node.broadcasts).hasSize(1);
    }

    @Test
    void receiptPollingRepairsRolledBackConfirmationWithoutNewRecords() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(jobId);
        var before = receiptState(jobId);
        UUID auditId = jdbc.queryForObject("SELECT audit_event_id FROM blockchain_jobs WHERE id = ?", UUID.class, jobId);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM blockchain_jobs WHERE id = ?", UUID.class, jobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 107, null);

        // Fail the confirmation commit for this fixture only, after the worker sees a valid receipt.
        jdbc.execute("ALTER TABLE blockchain_jobs ADD CONSTRAINT c08_fixture_reject_confirmation "
                + "CHECK (id <> '" + jobId + "'::uuid OR status <> 'CONFIRMED')");
        try {
            assertThatThrownBy(() -> worker.pollSubmitted())
                    .rootCause().isInstanceOfSatisfying(org.postgresql.util.PSQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo("23514"));
            assertThat(receiptState(jobId)).isEqualTo(before);
        } finally {
            jdbc.execute("ALTER TABLE blockchain_jobs DROP CONSTRAINT c08_fixture_reject_confirmation");
        }

        // Model time after the original polling lease expires while keeping the same RPC observation.
        jdbc.update("UPDATE blockchain_jobs SET lease_until = now() - interval '1 minute' WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        var repaired = receiptState(jobId);
        assertThat(repaired).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED")
                .containsEntry("attempt_id", before.get("attempt_id")).containsEntry("tx_hash", before.get("tx_hash"))
                .containsEntry("nonce", before.get("nonce")).containsEntry("event_id", before.get("event_id"))
                .containsEntry("receipt_status", 1).containsEntry("block_number", 107L);
        assertThat((String) repaired.get("receipt")).contains((String) before.get("tx_hash"));
        assertThat(worker.pollSubmitted()).isEmpty();
        assertThat(receiptState(jobId)).isEqualTo(repaired);
        assertThat(count("verifications", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_VERIFIED'",
                Long.class, caseId)).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", auditId)).isEqualTo(1);
        assertThat(node.broadcasts).hasSize(1);
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
        assertThat(pending.reasonCode()).isEqualTo("RECEIPT_PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, jobId)).isEqualTo("SUBMITTED");
        var before = receiptState(jobId);
        byte[] ciphertext = jdbc.queryForObject("SELECT signed_tx_ciphertext FROM blockchain_transactions WHERE id = ?",
                byte[].class, before.get("attempt_id"));
        String persistedRaw = Numeric.toHexString(cipher.decrypt(ciphertext, JobSecretCipher.associatedData(jobId, "signed-tx")));

        // Three minutes later, still no receipt: the same bytes are re-sent, nothing is re-signed.
        jdbc.update("UPDATE blockchain_transactions SET submitted_at = now() - interval '3 minutes' WHERE blockchain_job_id = ?", jobId);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        var rebroadcast = worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();

        assertThat(rebroadcast.outcome()).isEqualTo(CaseVerifiedWorker.PollOutcome.REBROADCAST);
        assertThat(rebroadcast.reasonCode()).isEqualTo("REBROADCAST");
        assertThat(node.broadcasts).hasSize(2);
        assertThat(node.broadcasts).containsExactly(persistedRaw, persistedRaw);
        assertThat(TransactionDecoder.decode(node.broadcasts.get(1)).getNonce().longValueExact()).isEqualTo(before.get("nonce"));
        assertThat(org.web3j.crypto.Hash.sha3(node.broadcasts.get(1))).isEqualTo(before.get("tx_hash"));
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, jobId)).isEqualTo("SUBMITTED");

        node.mine(persistedRaw, REGISTRY, 108, null);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        assertThat(receiptState(jobId)).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED")
                .containsEntry("attempt_id", before.get("attempt_id")).containsEntry("event_id", before.get("event_id"))
                .containsEntry("tx_hash", before.get("tx_hash")).containsEntry("nonce", before.get("nonce"));
        assertThat(node.broadcasts).hasSize(2);
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
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC(), new SignerSessionLock(jdbc.getDataSource()));
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
                new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC(), new SignerSessionLock(jdbc.getDataSource()));
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

    @Test
    void aSecondCaseWaitsForTheSignerAndNeverReusesAJournaledNonce() throws Exception {
        UUID first = newLocalCaseVerifiedJob();
        worker.processNextJob().orElseThrow();
        String raw = node.broadcasts.get(0);
        long firstNonce = TransactionDecoder.decode(raw).getNonce().longValueExact();
        UUID second = newLocalCaseVerifiedJob();
        assertThat(worker.processNextJob()).isEmpty();
        assertThat(count("blockchain_transactions", "blockchain_job_id", second)).isZero();
        node.mine(raw, REGISTRY, 104, null);
        worker.pollSubmitted();
        assertThat(jdbc.queryForObject("SELECT status FROM blockchain_jobs WHERE id = ?", String.class, first))
                .isEqualTo("CONFIRMED");
        node.pendingNonce = java.math.BigInteger.ZERO; // stale node must not cause nonce reuse
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(second);
        assertThat(TransactionDecoder.decode(node.broadcasts.get(1)).getNonce().longValueExact()).isEqualTo(firstNonce + 1);
    }

    @Test
    void expiredJobLeaseCannotSupersedeALiveSignerSession() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        var signing = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        node.beforeSign = () -> {
            signing.countDown();
            try {
                if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("TEST_TIMEOUT");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        };
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var running = pool.submit(() -> worker.processNextJob());
            assertThat(signing.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE blockchain_jobs SET lease_until = now() - interval '1 minute' WHERE id = ?", jobId);
            var second = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                    new CaseVerifiedManifestBuilder(json), node, cipher, properties, Clock.systemUTC(),
                    new SignerSessionLock(jdbc.getDataSource()));
            assertThat(second.processNextJob()).isEmpty();
            assertThat(second.pollSubmitted()).isEmpty();
            release.countDown();
            assertThat(running.get(15, java.util.concurrent.TimeUnit.SECONDS)).isPresent();
            assertThat(node.broadcasts).hasSize(1);
            assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void lostDatabaseSessionFencesTheOldWorkerBeforePersistOrBroadcast() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        node.beforeSign = () -> {
            String lockKey = "aidbridge:signer:31337:" + HARDHAT_ADDRESS_0;
            Integer pid = jdbc.queryForObject("""
                    SELECT pid FROM pg_locks WHERE locktype = 'advisory' AND granted AND objsubid = 1
                      AND classid = ((hashtextextended(?, 0) >> 32) & 4294967295)::oid
                      AND objid = (hashtextextended(?, 0) & 4294967295)::oid
                      AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
                    """, Integer.class, lockKey, lockKey);
            assertThat(jdbc.queryForObject("SELECT pg_terminate_backend(?)", Boolean.class, pid)).isTrue();
        };
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> worker.processNextJob())
                .hasMessage("SIGNER_LOCK_LOST");
        assertThat(node.broadcasts).isEmpty();
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isZero();
        node.beforeSign = () -> { };
        jdbc.update("UPDATE blockchain_jobs SET lease_until = now() - interval '1 minute' WHERE id = ?", jobId);
        assertThat(worker.processNextJob().orElseThrow().outcome()).isEqualTo(CaseVerifiedWorker.Outcome.SUBMITTED);
        assertThat(node.broadcasts).hasSize(1);
    }

    @Test
    void recoveredAttemptCannotBeSentUnderAnotherRegistry() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        worker.processNextJob().orElseThrow();
        jdbc.update("UPDATE blockchain_transactions SET status = 'PREPARED', contract_address = ? WHERE blockchain_job_id = ?",
                "0x" + "22".repeat(20), jobId);
        jdbc.update("UPDATE blockchain_jobs SET status = 'PROCESSING', lease_until = now() - interval '1 minute' WHERE id = ?", jobId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> worker.processNextJob())
                .hasMessage("PREPARED_ATTEMPT_BINDING_MISMATCH");
        assertThat(node.broadcasts).hasSize(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
    }

    @Test
    void caseVerificationCannotBeSignedWithAMilestoneSequence() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        jdbc.update("UPDATE blockchain_jobs SET chain_sequence = 2 WHERE id = ?", jobId);
        assertThat(worker.processNextJob().orElseThrow().reasonCode()).isEqualTo("INVALID_CASE_VERIFICATION_SEQUENCE");
        assertThat(node.broadcasts).isEmpty();
    }

    @Test
    void predecessorRequiresConfirmedAttemptOnTheSameDeployment() throws Exception {
        UUID predecessorId = newLocalCaseVerifiedJob();
        UUID caseId = jdbc.queryForObject(
                "SELECT aid_case_id FROM blockchain_jobs WHERE id = ?", UUID.class, predecessorId);

        assertThat(jobs.findPredecessorState(caseId, 3L, 31337L, REGISTRY))
                .isEqualTo("PREDECESSOR_MISSING");
        assertThat(jobs.findPredecessorState(caseId, 2L, 31337L, REGISTRY))
                .isEqualTo("PREDECESSOR_NOT_CONFIRMED");

        jdbc.update("UPDATE blockchain_jobs SET status = 'FAILED' WHERE id = ?", predecessorId);
        assertThat(jobs.findPredecessorState(caseId, 2L, 31337L, REGISTRY))
                .isEqualTo("PREDECESSOR_FAILED");
        jdbc.update("UPDATE blockchain_jobs SET status = 'PENDING' WHERE id = ?", predecessorId);

        assertThat(worker.processNextJob().orElseThrow().outcome())
                .isEqualTo(CaseVerifiedWorker.Outcome.SUBMITTED);
        assertThat(jobs.findPredecessorState(caseId, 2L, 31337L, REGISTRY))
                .isEqualTo("PREDECESSOR_NOT_CONFIRMED");

        node.mine(node.broadcasts.get(0), REGISTRY, 100, null);
        assertThat(worker.pollSubmitted()).containsExactly(
                new CaseVerifiedWorker.PollResult(
                        predecessorId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));

        assertThat(jobs.findPredecessorState(caseId, 2L, 31337L, REGISTRY))
                .isEqualTo("READY");
        assertThat(jobs.findPredecessorState(caseId, 2L, 31337L, "0x" + "22".repeat(20)))
                .isEqualTo("PREDECESSOR_DEPLOYMENT_MISMATCH");
        assertThat(jobs.findPredecessorState(caseId, 2L, 43113L, REGISTRY))
                .isEqualTo("PREDECESSOR_DEPLOYMENT_MISMATCH");
    }

    @Test
    void localPledgeWaitsForVerifiedPredecessorThenConfirmsUsingOriginalRefs() throws Exception {
        UUID campaignId = publishedCampaign(actor(ORG_A, "ORG_ADMIN", false));
        List<UUID> itemIds = campaignItemIds(campaignId);
        var body = Map.of("items", List.of(
                Map.of("campaignItemId", itemIds.get(0), "quantity", 2),
                Map.of("campaignItemId", itemIds.get(1), "quantity", 1)));
        var response = donor(true).browser().send(
                "POST", "/api/campaigns/" + campaignId + "/commitments", body, newKey(), true);
        status(response, 201);
        UUID contributionId = UUID.fromString(node(response).path("id").asText());
        UUID pledgeAuditId = UUID.fromString(node(response).path("auditEventId").asText());
        UUID pledgeJobId = jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?",
                UUID.class, pledgeAuditId);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM blockchain_jobs WHERE id = ?",
                UUID.class, pledgeJobId);
        UUID verifiedJobId = jdbc.queryForObject("""
                SELECT id FROM blockchain_jobs WHERE aid_case_id = ? AND chain_sequence = 1
                """, UUID.class, caseId);
        assertThat(jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE id IN (?, ?)",
                verifiedJobId, pledgeJobId)).isEqualTo(2);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = now() + interval '1 hour' WHERE id = ?",
                verifiedJobId);

        node.beforeSign = () -> { throw new AssertionError("Pledge signed before its predecessor confirmed"); };
        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                pledgeJobId, CaseVerifiedWorker.Outcome.RETRY_SCHEDULED, "PREDECESSOR_NOT_CONFIRMED"));
        assertThat(jdbc.queryForMap("SELECT status, manifest_private FROM blockchain_jobs WHERE id = ?", pledgeJobId))
                .containsEntry("status", "PENDING").containsEntry("manifest_private", null);
        assertThat(count("blockchain_transactions", "blockchain_job_id", pledgeJobId)).isZero();
        assertThat(node.broadcasts).isEmpty();
        node.beforeSign = () -> { };

        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", verifiedJobId);
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(verifiedJobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 100, null);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                verifiedJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));

        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", pledgeJobId);
        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                pledgeJobId, CaseVerifiedWorker.Outcome.SUBMITTED, null));
        mineGoodsMilestone(pledgeJobId, "89f8809e", "AidCommitted", 101, 2, 2);

        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                pledgeJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        var state = receiptState(pledgeJobId);
        assertThat(state).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED");
        assertThat(json.readTree((String) state.get("receipt")).path("eventType").asText()).isEqualTo("AID_PLEDGED");
        assertThat(worker.processNextJob()).isEmpty();
        assertThat(count("blockchain_transactions", "blockchain_job_id", pledgeJobId)).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", pledgeAuditId)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT pledged_quantity FROM contribution_items WHERE contribution_id = ? ORDER BY pledged_quantity
                """, Integer.class, contributionId)).containsExactly(1, 2);
    }

    @Test
    void localReductionRequiresItsOriginalPledgeEvenWhenThePreviousJobIsConfirmed() throws Exception {
        UUID campaignId = publishedCampaign(actor(ORG_A, "ORG_ADMIN", false));
        List<UUID> campaignItems = campaignItemIds(campaignId);
        Actor originalDonor = donor(true);
        String pledgePath = "/api/campaigns/" + campaignId + "/commitments";
        var originalResponse = originalDonor.browser().send("POST", pledgePath, Map.of("items", List.of(
                Map.of("campaignItemId", campaignItems.get(0), "quantity", 2),
                Map.of("campaignItemId", campaignItems.get(1), "quantity", 1))), newKey(), true);
        status(originalResponse, 201);
        var original = node(originalResponse);
        UUID originalJobId = jobIdForAudit(UUID.fromString(original.path("auditEventId").asText()));
        String contributionId = original.path("id").asText();
        String lineId = jdbc.queryForObject("""
                SELECT id FROM contribution_items WHERE contribution_id = ? AND campaign_item_id = ?
                """, UUID.class, UUID.fromString(contributionId), campaignItems.get(0)).toString();

        var secondResponse = donor(true).browser().send("POST", pledgePath, Map.of("items", List.of(
                Map.of("campaignItemId", campaignItems.get(1), "quantity", 1))), newKey(), true);
        status(secondResponse, 201);
        UUID secondJobId = jobIdForAudit(UUID.fromString(node(secondResponse).path("auditEventId").asText()));

        String cancelPath = "/api/commitments/" + contributionId + "/cancellations";
        var cancelBody = Map.of("items", List.of(Map.of("contributionItemId", lineId, "quantity", 1)),
                "reason", "Partial stock adjustment");
        String cancelKey = newKey();
        var cancelled = originalDonor.browser().send("POST", cancelPath, cancelBody, cancelKey, true);
        status(cancelled, 200);
        UUID reductionAuditId = UUID.fromString(node(cancelled).path("auditEventId").asText());
        UUID reductionJobId = jobIdForAudit(reductionAuditId);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM blockchain_jobs WHERE id = ?",
                UUID.class, reductionJobId);
        assertThat(jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE aid_case_id = ?", caseId))
                .isEqualTo(4);

        UUID verifiedJobId = jdbc.queryForObject("""
                SELECT id FROM blockchain_jobs WHERE aid_case_id = ? AND chain_sequence = 1
                """, UUID.class, caseId);
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(verifiedJobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 110, null);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                verifiedJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(originalJobId);
        mineGoodsMilestone(originalJobId, "89f8809e", "AidCommitted", 111, 2, 2);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                originalJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(secondJobId);
        mineGoodsMilestone(secondJobId, "89f8809e", "AidCommitted", 112, 3, 1);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                secondJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));

        // Model an original pledge anchored at the wrong deployment in this isolated test schema.
        jdbc.update("UPDATE blockchain_transactions SET contract_address = ? WHERE blockchain_job_id = ?",
                "0x" + "22".repeat(20), originalJobId);
        node.beforeSign = () -> { throw new AssertionError("Reduction signed without its original pledge"); };
        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                reductionJobId, CaseVerifiedWorker.Outcome.RETRY_SCHEDULED, "ORIGINAL_PLEDGE_NOT_CONFIRMED"));
        assertThat(jdbc.queryForMap("SELECT status, manifest_private FROM blockchain_jobs WHERE id = ?", reductionJobId))
                .containsEntry("status", "PENDING").containsEntry("manifest_private", null);
        assertThat(count("blockchain_transactions", "blockchain_job_id", reductionJobId)).isZero();
        assertThat(node.broadcasts).hasSize(3);
        node.beforeSign = () -> { };

        jdbc.update("UPDATE blockchain_transactions SET contract_address = ? WHERE blockchain_job_id = ?",
                REGISTRY, originalJobId);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", reductionJobId);
        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                reductionJobId, CaseVerifiedWorker.Outcome.SUBMITTED, null));
        String reductionSelector = Hash.sha3String(
                "recordCommitmentReduction(bytes32,bytes32,bytes32,uint64,bytes32,uint16,bytes32)").substring(2, 10);
        mineGoodsMilestone(reductionJobId, reductionSelector, "CommitmentReduced", 113, 4, 1);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                reductionJobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        var state = receiptState(reductionJobId);
        assertThat(state).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED");
        assertThat(json.readTree((String) state.get("receipt")).path("eventType").asText())
                .isEqualTo("COMMITMENT_REDUCED");
        var manifest = json.readTree(jdbc.queryForObject(
                "SELECT CAST(manifest_private AS text) FROM blockchain_jobs WHERE id = ?", String.class, reductionJobId));
        assertThat(manifest.path("reason").asText()).isEqualTo("Partial stock adjustment");
        assertThat(manifest.path("lines").get(0).path("contributionItemId").asText()).isEqualTo(lineId);
        assertThat(manifest.path("lines").get(0).path("quantity").asInt()).isEqualTo(1);

        var replay = originalDonor.browser().send("POST", cancelPath, cancelBody, cancelKey, true);
        status(replay, 200);
        assertThat(node(replay).path("auditEventId").asText()).isEqualTo(reductionAuditId.toString());
        assertThat(worker.processNextJob()).isEmpty();
        assertThat(count("blockchain_jobs", "audit_event_id", reductionAuditId)).isEqualTo(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", reductionJobId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancelled_quantity FROM contribution_items WHERE id = ?",
                Integer.class, UUID.fromString(lineId))).isEqualTo(1);
    }

    private UUID jobIdForAudit(UUID auditEventId) {
        return jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, auditEventId);
    }

    @Test
    void localFiveMilestonesConfirmWithStoredReceiptAndDeliveryRefs() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> itemIds = campaignItemIds(campaignId);
        var pledged = donor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments",
                Map.of("items", List.of(Map.of("campaignItemId", itemIds.get(0), "quantity", 2),
                        Map.of("campaignItemId", itemIds.get(1), "quantity", 2))), newKey(), true);
        status(pledged, 201);
        var pledge = node(pledged);
        UUID pledgeJobId = jobIdForAudit(UUID.fromString(pledge.path("auditEventId").asText()));
        String commitmentPath = "/api/commitments/" + pledge.path("id").asText();
        String firstLine = pledge.path("items").get(0).path("id").asText();
        String secondLine = pledge.path("items").get(1).path("id").asText();
        var reduction = donor.browser().send("POST", commitmentPath + "/cancellations", Map.of(
                "reason", "Partial stock adjustment", "items", List.of(Map.of("contributionItemId", firstLine,
                        "quantity", 1))), newKey(), true);
        status(reduction, 200);
        UUID reductionJobId = jobIdForAudit(UUID.fromString(node(reduction).path("auditEventId").asText()));

        var receiptRequest = Map.of("receivedAt", Instant.now().toString(), "items", List.of(
                Map.of("contributionItemId", firstLine, "quantity", 1),
                Map.of("contributionItemId", secondLine, "quantity", 2)));
        String receiptKey = newKey();
        var receiptResponse = orgAdmin.browser().send("POST", commitmentPath + "/receipts", receiptRequest,
                receiptKey, true);
        status(receiptResponse, 201);
        var receipt = node(receiptResponse);
        UUID receiptId = UUID.fromString(receipt.path("id").asText());
        UUID receiptAuditId = UUID.fromString(receipt.path("auditEventId").asText());
        UUID receiptJobId = jobIdForAudit(receiptAuditId);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM blockchain_jobs WHERE id = ?", UUID.class, receiptJobId);
        List<UUID> evidenceIds = jdbc.queryForList("""
                SELECT DISTINCT evidence_version_id FROM case_evidence_links
                WHERE aid_case_id = ? ORDER BY evidence_version_id
                """, UUID.class, caseId);
        String deliveryPath = "/api/cases/" + caseId + "/deliveries";
        var deliveryRequest = Map.of("outcome", "DELIVERED", "handedOverAt", Instant.now().toString(),
                "items", List.of(Map.of("campaignItemId", itemIds.get(0), "quantity", 1),
                        Map.of("campaignItemId", itemIds.get(1), "quantity", 1)), "evidenceVersionIds", evidenceIds);
        String deliveryKey = newKey();
        var deliveryResponse = volunteer.browser().send("POST", deliveryPath, deliveryRequest, deliveryKey, true);
        status(deliveryResponse, 201);
        var delivery = node(deliveryResponse);
        assertThat(delivery.path("reviewStatus").asText()).isEqualTo("PENDING");
        UUID deliveryId = UUID.fromString(delivery.path("id").asText());
        UUID deliveryAuditId = UUID.fromString(delivery.path("auditEventId").asText());
        UUID deliveryJobId = jobIdForAudit(deliveryAuditId);
        assertThat(jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE aid_case_id = ?", caseId)).isEqualTo(5);

        UUID verifiedJobId = jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE aid_case_id = ? AND chain_sequence = 1",
                UUID.class, caseId);
        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(verifiedJobId);
        node.mine(node.broadcasts.get(0), REGISTRY, 120, null);
        assertConfirmed(verifiedJobId);

        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(pledgeJobId);
        mineGoodsMilestone(pledgeJobId, "89f8809e", "AidCommitted", 121, 2, 2);
        assertConfirmed(pledgeJobId);

        assertThat(worker.processNextJob().orElseThrow().jobId()).isEqualTo(reductionJobId);
        String reductionSelector = Hash.sha3String(
                "recordCommitmentReduction(bytes32,bytes32,bytes32,uint64,bytes32,uint16,bytes32)").substring(2, 10);
        mineGoodsMilestone(reductionJobId, reductionSelector, "CommitmentReduced", 122, 3, 1);
        assertConfirmed(reductionJobId);

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                receiptJobId, CaseVerifiedWorker.Outcome.SUBMITTED, null));
        mineGoodsMilestone(receiptJobId, "73a5f8b9", "GoodsReceived", 123, 4, 2);
        assertConfirmed(receiptJobId);
        var state = receiptState(receiptJobId);
        assertThat(state).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED");
        assertThat(json.readTree((String) state.get("receipt")).path("eventType").asText()).isEqualTo("GOODS_RECEIVED");

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                deliveryJobId, CaseVerifiedWorker.Outcome.SUBMITTED, null));
        mineGoodsMilestone(deliveryJobId, "2df45447", "DeliveryRecorded", 124, 5, 2);
        assertConfirmed(deliveryJobId);
        var deliveryState = receiptState(deliveryJobId);
        assertThat(deliveryState).containsEntry("job_status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED");
        assertThat(json.readTree((String) deliveryState.get("receipt")).path("eventType").asText())
                .isEqualTo("DELIVERY_RECORDED");
        var manifest = json.readTree(jdbc.queryForObject(
                "SELECT CAST(manifest_private AS text) FROM blockchain_jobs WHERE id = ?", String.class, deliveryJobId));
        assertThat(StreamSupport.stream(manifest.path("evidenceVersions").spliterator(), false)
                .map(version -> UUID.fromString(version.path("evidenceVersionId").asText())).toList())
                .containsExactlyInAnyOrderElementsOf(evidenceIds);

        var replay = orgAdmin.browser().send("POST", commitmentPath + "/receipts", receiptRequest, receiptKey, true);
        status(replay, 201);
        assertThat(node(replay).path("auditEventId").asText()).isEqualTo(receiptAuditId.toString());
        var deliveryReplay = volunteer.browser().send("POST", deliveryPath, deliveryRequest, deliveryKey, true);
        status(deliveryReplay, 201);
        assertThat(node(deliveryReplay).path("auditEventId").asText()).isEqualTo(deliveryAuditId.toString());
        assertThat(worker.processNextJob()).isEmpty();
        assertThat(count("blockchain_jobs", "audit_event_id", receiptAuditId)).isEqualTo(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", receiptJobId)).isEqualTo(1);
        assertThat(count("goods_receipts", "contribution_id", UUID.fromString(pledge.path("id").asText()))).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", deliveryAuditId)).isEqualTo(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", deliveryJobId)).isEqualTo(1);
        assertThat(count("deliveries", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT delivered_quantity FROM delivery_items WHERE delivery_id = ? ORDER BY campaign_item_id
                """, Integer.class, deliveryId)).containsExactly(1, 1);
        assertThat(jdbc.queryForList("""
                SELECT received_quantity FROM goods_receipt_items WHERE goods_receipt_id = ? ORDER BY contribution_item_id
                """, Integer.class, receiptId)).containsExactlyInAnyOrder(1, 2);
    }

    private void assertConfirmed(UUID jobId) {
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
    }

    private void mineGoodsMilestone(UUID jobId, String selector, String eventName, long blockNumber,
            long expectedSequence, int expectedLines) {
        var row = jdbc.queryForMap("""
                SELECT o.public_ref AS org_ref, c.public_ref AS case_ref, j.logical_event_id,
                       j.chain_sequence, j.schema_version, j.evidence_commitment,
                       CAST(j.manifest_private AS text) AS manifest, t.tx_hash,
                       COALESCE(d.delivery_ref, x.public_ref) AS operation_ref,
                       a.payload_private ->> 'receiptRef' AS receipt_ref,
                       a.payload_private ->> 'deliveryRef' AS audit_delivery_ref
                FROM blockchain_jobs j
                JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                JOIN audit_events a ON a.id = j.audit_event_id
                LEFT JOIN goods_receipts r ON a.event_type = 'GOODS_RECEIVED' AND r.id = a.entity_id
                LEFT JOIN deliveries d ON a.event_type = 'DELIVERY_RECORDED' AND d.id = a.entity_id
                LEFT JOIN contributions x ON d.id IS NULL AND x.id = COALESCE(r.contribution_id, a.entity_id)
                JOIN aid_cases c ON c.id = j.aid_case_id
                JOIN organizations o ON o.id = c.organization_id
                WHERE j.id = ?
                """, jobId);
        assertThat(row.get("chain_sequence")).isEqualTo(expectedSequence);
        String ref = Numeric.toHexString((byte[]) row.get("operation_ref"));
        var manifest = json.readTree((String) row.get("manifest"));
        if ("DeliveryRecorded".equals(eventName)) {
            assertThat(row.get("audit_delivery_ref")).isEqualTo(ref);
            assertThat(manifest.path("deliveryRef").asText()).isEqualTo(ref);
        } else {
            assertThat(manifest.path("commitmentRef").asText()).isEqualTo(ref);
        }
        assertThat(manifest.path("lines").size()).isEqualTo(expectedLines);
        String receiptRef = (String) row.get("receipt_ref");
        String eventExtraTypes = ",bytes32";
        String refWords = Numeric.cleanHexPrefix(ref);
        if (receiptRef != null) {
            assertThat(manifest.path("receiptRef").asText()).isEqualTo(receiptRef);
            refWords = Numeric.cleanHexPrefix(receiptRef) + refWords;
            eventExtraTypes += ",bytes32";
        }

        String raw = node.broadcasts.getLast();
        String calldata = Numeric.cleanHexPrefix(TransactionDecoder.decode(raw).getData());
        assertThat(calldata).startsWith(selector).endsWith(refWords).hasSize(8 + 6 * 64 + refWords.length());
        String hash = (String) row.get("tx_hash");
        BigInteger block = BigInteger.valueOf(blockNumber);
        String blockHash = "0x" + String.format("%064x", blockNumber);
        String logData = "0x" + String.format("%064x", ((Number) row.get("chain_sequence")).longValue())
                + Numeric.cleanHexPrefix(Numeric.toHexString((byte[]) row.get("evidence_commitment")))
                + String.format("%064x%064x", ((Number) row.get("schema_version")).intValue(), 1_762_650_000L)
                + refWords;
        var log = new ChainReceipt.ChainLog(REGISTRY, List.of(
                Hash.sha3String(eventName + "(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64" + eventExtraTypes + ")"),
                Numeric.toHexString((byte[]) row.get("org_ref")), Numeric.toHexString((byte[]) row.get("case_ref")),
                Numeric.toHexString((byte[]) row.get("logical_event_id"))), logData, BigInteger.ZERO, false);
        node.canonicalBlocks.put(block, blockHash);
        node.receipts.put(hash, new ChainReceipt(hash, 1, block, blockHash,
                HARDHAT_ADDRESS_0, REGISTRY, List.of(log)));
    }

    private Map<String, Object> receiptState(UUID jobId) {
        return jdbc.queryForMap("""
                SELECT j.status AS job_status, j.last_error, t.id AS attempt_id, t.status AS attempt_status,
                       encode(j.logical_event_id, 'hex') AS event_id, t.tx_hash, t.nonce, t.error_message,
                       CAST(t.receipt_status AS integer) AS receipt_status, t.confirmed_at, t.block_number,
                       CAST(t.receipt_private AS text) AS receipt
                FROM blockchain_jobs j JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                WHERE j.id = ?
                """, jobId);
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

    @ParameterizedTest
    @CsvSource({"MISSING_INDEX, RECEIPT_LOG_INDEX_INVALID", "NEGATIVE_INDEX, RECEIPT_LOG_INDEX_INVALID",
        "OVERSIZED_INDEX, RECEIPT_LOG_INDEX_INVALID", "STATUS_TWO, RECEIPT_STATUS_INVALID",
        "STATUS_NEGATIVE, RECEIPT_STATUS_INVALID", "RAW_STATUS_MISSING, RECEIPT_STATUS_INVALID",
        "RAW_STATUS_INVALID, RECEIPT_STATUS_INVALID"})
    void malformedProofRemainsPendingAndValidProofRecoversSameAttempt(String fixture, String reason) throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        worker.processNextJob().orElseThrow();
        var before = receiptState(jobId);
        String hash = (String) before.get("tx_hash");
        node.mine(node.broadcasts.get(0), REGISTRY, 109, null);
        ChainReceipt valid = node.receipts.get(hash);
        if (fixture.startsWith("RAW_STATUS")) {
            // Adapter unit tests cover parsing null/abnormal JSON-RPC status into this RPC error.
            node.nextReceiptFailure = new BlockchainRpcException(reason, "Malformed RPC status");
        } else if (fixture.startsWith("STATUS")) {
            node.receipts.put(hash, new ChainReceipt(hash, fixture.equals("STATUS_TWO") ? 2 : -1,
                    valid.blockNumber(), valid.blockHash(), valid.from(), valid.to(), valid.logs()));
        } else {
            var log = valid.logs().get(0);
            java.math.BigInteger index = fixture.equals("MISSING_INDEX") ? null
                    : fixture.equals("NEGATIVE_INDEX") ? java.math.BigInteger.valueOf(-1)
                    : java.math.BigInteger.ONE.shiftLeft(63);
            var badLog = new ChainReceipt.ChainLog(log.address(), log.topics(), log.data(), index, false);
            node.receipts.put(hash, new ChainReceipt(hash, 1, valid.blockNumber(), valid.blockHash(),
                    valid.from(), valid.to(), List.of(badLog)));
        }
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                jobId, CaseVerifiedWorker.PollOutcome.PENDING, reason));
        assertThat(receiptState(jobId)).containsEntry("job_status", "SUBMITTED")
                .containsEntry("attempt_status", "SUBMITTED").containsEntry("receipt_status", null)
                .containsEntry("confirmed_at", null).containsEntry("receipt", null)
                .containsEntry("attempt_id", before.get("attempt_id")).containsEntry("event_id", before.get("event_id"))
                .containsEntry("nonce", before.get("nonce")).containsEntry("tx_hash", hash);
        node.receipts.put(hash, valid);
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);
        assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                jobId, CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
        var recovered = receiptState(jobId);
        assertThat(recovered).containsEntry("attempt_id", before.get("attempt_id"))
                .containsEntry("event_id", before.get("event_id")).containsEntry("nonce", before.get("nonce"))
                .containsEntry("tx_hash", hash).containsEntry("receipt_status", 1);
        assertThat(worker.pollSubmitted()).isEmpty();
        assertThat(receiptState(jobId)).isEqualTo(recovered);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM blockchain_transactions WHERE blockchain_job_id = ?",
                Long.class, jobId)).isEqualTo(1);
        assertThat(node.broadcasts).hasSize(1);
    }

    @Test
    void keepsPreparedCaseVerificationWhileRpcReportsWrongChain() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        var interruptedNode = new FakeBlockchainAdapter(properties) {
            @Override
            public String broadcastRawTransaction(String rawTransactionHex) {
                throw new IllegalStateException("TEST_STOP_BEFORE_BROADCAST");
            }
        };
        interruptedNode.pendingNonce = node.pendingNonce;
        var interruptedWorker = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), interruptedNode, cipher, properties, Clock.systemUTC(),
                new SignerSessionLock(jdbc.getDataSource()));
        assertThatThrownBy(interruptedWorker::processNextJob).hasMessage("TEST_STOP_BEFORE_BROADCAST");
        assertThat(interruptedNode.broadcasts).isEmpty();

        var prepared = receiptState(jobId);
        assertThat(prepared).containsEntry("job_status", "PROCESSING").containsEntry("attempt_status", "PREPARED");
        var identity = Map.of("attempt_id", prepared.get("attempt_id"), "event_id", prepared.get("event_id"),
                "nonce", prepared.get("nonce"), "tx_hash", prepared.get("tx_hash"));
        String ciphertextSql = "SELECT signed_tx_ciphertext FROM blockchain_transactions WHERE id = ?";
        byte[] originalCiphertext = jdbc.queryForObject(ciphertextSql, byte[].class, identity.get("attempt_id"));
        String originalRaw = Numeric.toHexString(cipher.decrypt(originalCiphertext,
                JobSecretCipher.associatedData(jobId, "signed-tx")));
        assertThat(jdbc.update("UPDATE blockchain_jobs SET lease_until = now() - interval '1 second' WHERE id = ?",
                jobId)).isEqualTo(1);

        node.beforeSign = () -> { throw new AssertionError("Recovery must not sign a replacement transaction"); };
        var originalPendingNonce = node.pendingNonce;
        node.chainId = 43113;

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                jobId, CaseVerifiedWorker.Outcome.RETRY_SCHEDULED, "INVALID_CHAIN_ID"));
        assertThat(node.broadcasts).isEmpty();
        assertThat(node.pendingNonce).isEqualTo(originalPendingNonce);
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        assertThat(receiptState(jobId)).containsAllEntriesOf(identity)
                .containsEntry("job_status", "PROCESSING").containsEntry("attempt_status", "PREPARED")
                .containsEntry("last_error", "INVALID_CHAIN_ID");
        var retry = jdbc.queryForMap("SELECT lease_until, next_retry_at FROM blockchain_jobs WHERE id = ?", jobId);
        assertThat(retry).containsEntry("lease_until", null);
        assertThat(retry.get("next_retry_at")).isNotNull();
        byte[] preservedCiphertext = jdbc.queryForObject(ciphertextSql, byte[].class, identity.get("attempt_id"));
        assertThat(java.util.Arrays.equals(originalCiphertext, preservedCiphertext))
                .as("the persisted ciphertext is unchanged").isTrue();

        node.chainId = 31337;
        assertThat(jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId)).isEqualTo(1);

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                jobId, CaseVerifiedWorker.Outcome.REBROADCAST, null));
        assertThat(node.broadcasts).hasSize(1);
        assertThat(originalRaw.equals(node.broadcasts.getFirst()))
                .as("recovery broadcasts the original signed bytes").isTrue();
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        assertThat(receiptState(jobId)).containsAllEntriesOf(identity)
                .containsEntry("job_status", "SUBMITTED").containsEntry("attempt_status", "SUBMITTED");
    }

    @Test
    void doesNotPrepareCaseVerificationWhileRpcReportsWrongChain() throws Exception {
        UUID jobId = newLocalCaseVerifiedJob();
        String originalEventId = jdbc.queryForObject(
                "SELECT encode(logical_event_id, 'hex') FROM blockchain_jobs WHERE id = ?",
                String.class, jobId);
        node.chainId = 43113;

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(
                new CaseVerifiedWorker.Result(
                        jobId, CaseVerifiedWorker.Outcome.RETRY_SCHEDULED, "INVALID_CHAIN_ID"));
        assertThat(jdbc.queryForMap(
                "SELECT status, last_error, lease_until FROM blockchain_jobs WHERE id = ?", jobId))
                .containsEntry("status", "PROCESSING")
                .containsEntry("last_error", "INVALID_CHAIN_ID")
                .containsEntry("lease_until", null);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM blockchain_transactions WHERE blockchain_job_id = ?",
                Long.class, jobId)).isZero();
        assertThat(node.broadcasts).isEmpty();

        node.chainId = 31337;
        jdbc.update("UPDATE blockchain_jobs SET next_retry_at = NULL WHERE id = ?", jobId);

        assertThat(worker.processNextJob().orElseThrow()).isEqualTo(
                new CaseVerifiedWorker.Result(jobId, CaseVerifiedWorker.Outcome.SUBMITTED, null));
        assertThat(node.broadcasts).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM blockchain_transactions WHERE blockchain_job_id = ? AND status = 'SUBMITTED'",
                Long.class, jobId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT encode(logical_event_id, 'hex') FROM blockchain_jobs WHERE id = ?",
                String.class, jobId)).isEqualTo(originalEventId);
    }
}
