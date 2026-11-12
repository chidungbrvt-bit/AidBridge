package com.aidbridge.blockchain.worker;

import com.aidbridge.blockchain.adapter.BlockchainAdapter;
import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.BlockchainRpcException;
import com.aidbridge.blockchain.adapter.ChainReceipt;
import com.aidbridge.blockchain.adapter.SignedTransaction;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.entity.BlockchainTransaction;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CanonicalJson;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.envelope.EvidenceCommitments;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.shared.util.ChainRefs;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.utils.Numeric;

/**
 * C05 submission path for CASE_VERIFIED jobs. Every SQL step is a short transaction and no RPC call runs
 * while a transaction is open. The signed attempt is persisted (PREPARED) before broadcast; after a crash
 * the same bytes are re-broadcast instead of signing a new transaction.
 */
public class CaseVerifiedWorker {

    static final Duration LEASE = Duration.ofSeconds(60);
    static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    static final Duration BINDING_RETRY_DELAY = Duration.ofMinutes(5);

    private final TransactionTemplate tx;
    private final BlockchainJobRepository jobs;
    private final BlockchainTransactionRepository attempts;
    private final CaseVerifiedManifestBuilder manifests;
    private final BlockchainAdapter adapter;
    private final JobSecretCipher cipher;
    private final BlockchainProperties properties;
    private final Clock clock;

    public CaseVerifiedWorker(TransactionTemplate tx, BlockchainJobRepository jobs,
            BlockchainTransactionRepository attempts, CaseVerifiedManifestBuilder manifests, BlockchainAdapter adapter,
            JobSecretCipher cipher, BlockchainProperties properties, Clock clock) {
        this.tx = tx;
        this.jobs = jobs;
        this.attempts = attempts;
        this.manifests = manifests;
        this.adapter = adapter;
        this.cipher = cipher;
        this.properties = properties;
        this.clock = clock;
    }

    /** What happened to the job picked in this run (for logs and tests; contains no secrets). */
    public enum Outcome { SUBMITTED, REBROADCAST, BINDING_MISSING, RETRY_SCHEDULED, FAILED }

    public record Result(UUID jobId, Outcome outcome, String reasonCode) {
    }

    private record Claim(UUID jobId, CaseVerifiedEnvelope envelope, UUID openAttemptId) {
    }

    /** Processes at most one CASE_VERIFIED job; empty when nothing is due. */
    public Optional<Result> processNextJob() {
        Instant now = clock.instant();
        Object claimed = tx.execute(status -> claimNext(now));
        if (claimed == null) {
            return Optional.empty();
        }
        if (claimed instanceof Result skipped) {
            return Optional.of(skipped);
        }
        Claim claim = (Claim) claimed;

        String rawTransaction;
        String expectedHash;
        UUID attemptId;
        boolean rebroadcast = claim.openAttemptId() != null;
        if (rebroadcast) {
            BlockchainTransaction existing = attempts.findById(claim.openAttemptId()).orElseThrow();
            rawTransaction = Numeric.toHexString(cipher.decrypt(existing.getSignedTxCiphertext(),
                    JobSecretCipher.associatedData(claim.jobId(), "signed-tx")));
            expectedHash = existing.getTxHash();
            attemptId = existing.getId();
        } else {
            BigInteger gasPrice = adapter.gasPrice().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
            if (gasPrice.compareTo(properties.maxGasPriceWei()) > 0) {
                return Optional.of(retry(claim.jobId(), "GAS_PRICE_ABOVE_CAP"));
            }
            String signer = adapter.relayerAddress();
            BigInteger nonce = adapter.pendingNonce(signer);
            Long reserved = attempts.maxOpenNonce(properties.chainId(), signer);
            if (reserved != null && BigInteger.valueOf(reserved + 1).compareTo(nonce) > 0) {
                nonce = BigInteger.valueOf(reserved + 1);
            }
            SignedTransaction signed = adapter.signTransaction(properties.registryAddress(),
                    AidRegistryAbi.encodeRecordCaseVerification(claim.envelope()), nonce, gasPrice, properties.gasLimit());
            try {
                attemptId = tx.execute(status -> {
                    BlockchainJob job = jobs.findById(claim.jobId()).orElseThrow();
                    byte[] sealed = cipher.encrypt(Numeric.hexStringToByteArray(signed.rawTransactionHex()),
                            JobSecretCipher.associatedData(claim.jobId(), "signed-tx"));
                    return attempts.saveAndFlush(BlockchainTransaction.prepared(job, signed, sealed, clock.instant())).getId();
                });
            } catch (DataIntegrityViolationException e) {
                // Another attempt already holds this (chain, signer, nonce); nothing was broadcast.
                return Optional.of(retry(claim.jobId(), "NONCE_CONFLICT"));
            }
            rawTransaction = signed.rawTransactionHex();
            expectedHash = signed.transactionHash();
        }

        String broadcastError = null;
        try {
            String returnedHash = adapter.broadcastRawTransaction(rawTransaction);
            if (returnedHash == null || !returnedHash.equalsIgnoreCase(expectedHash)) {
                broadcastError = "TX_HASH_MISMATCH";
            }
        } catch (BlockchainRpcException e) {
            // Unknown or rejected outcome. The bytes are persisted; receipt polling (part 5) decides, and
            // "already known"/"nonce too low" are expected after a crash-time rebroadcast.
            broadcastError = e.code();
        }

        String reason = broadcastError;
        tx.executeWithoutResult(status -> {
            BlockchainTransaction attempt = attempts.findById(attemptId).orElseThrow();
            if (BlockchainTransaction.PREPARED.equals(attempt.getStatus())) {
                attempt.markSubmitted(clock.instant());
            }
            if (reason != null) {
                attempt.recordError(reason);
            }
            BlockchainJob job = jobs.findById(claim.jobId()).orElseThrow();
            job.markSubmitted();
        });
        return Optional.of(new Result(claim.jobId(), rebroadcast ? Outcome.REBROADCAST : Outcome.SUBMITTED, reason));
    }

    /** TX1: lock one due job, check the org binding, prepare once, take a lease. No RPC here. */
    private Object claimNext(Instant now) {
        Optional<BlockchainJob> next = jobs.lockNextCaseVerifiedJob(properties.chainId(), now);
        if (next.isEmpty()) {
            return null;
        }
        BlockchainJob job = next.get();
        Optional<CaseVerifiedSource> found = jobs.findCaseVerifiedSource(job.getId());
        if (found.isEmpty()) {
            job.markFailed("CASE_VERIFIED_SOURCE_MISSING");
            return new Result(job.getId(), Outcome.FAILED, "CASE_VERIFIED_SOURCE_MISSING");
        }
        CaseVerifiedSource source = found.get();

        String relayer = adapter.relayerAddress();
        if (!"APPROVED".equals(source.getOrganizationStatus()) || source.getRelayerAddress() == null
                || !relayer.equalsIgnoreCase(source.getRelayerAddress())) {
            job.scheduleRetry("ORGANIZATION_RELAYER_NOT_BOUND", now.plus(BINDING_RETRY_DELAY));
            return new Result(job.getId(), Outcome.BINDING_MISSING, "ORGANIZATION_RELAYER_NOT_BOUND");
        }

        if (!job.isPrepared()) {
            CaseVerifiedManifestBuilder.Manifest manifest;
            try {
                manifest = manifests.build(source, job.getLogicalEventId(), job.getSchemaVersion(),
                        ChainRefs.randomBytes32());
            } catch (IllegalStateException | IllegalArgumentException e) {
                // Malformed private source: integrity problem for a human, not something to retry forever.
                String code = reasonCode(e, "MANIFEST_INVALID");
                job.markFailed(code);
                return new Result(job.getId(), Outcome.FAILED, code);
            }
            byte[] salt = ChainRefs.randomBytes32();
            byte[] commitment = EvidenceCommitments.evidenceCommitment(properties.chainId(),
                    properties.registryAddress(), source.getCasePublicRef(), job.getLogicalEventId(),
                    EvidenceCommitments.manifestDigest(manifest.canonicalUtf8()), salt);
            job.attachPreparation(manifest.canonical(),
                    cipher.encrypt(salt, JobSecretCipher.associatedData(job.getId(), "salt")), commitment);
        }
        job.claim(now.plus(LEASE));

        CaseVerifiedEnvelope envelope = new CaseVerifiedEnvelope(source.getOrgPublicRef(), source.getCasePublicRef(),
                job.getLogicalEventId(), job.getChainSequence(), job.getEvidenceCommitment(), job.getSchemaVersion());
        UUID openAttempt = attempts.findByJob_IdOrderByPreparedAtAsc(job.getId()).stream()
                .filter(a -> BlockchainTransaction.PREPARED.equals(a.getStatus()))
                .max(Comparator.comparing(BlockchainTransaction::getPreparedAt))
                .map(BlockchainTransaction::getId)
                .orElse(null);
        return new Claim(job.getId(), envelope, openAttempt);
    }

    /** Stable code = text before the first ':' of our own exception messages (no payload data). */
    static String reasonCode(RuntimeException e, String fallback) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        int colon = message.indexOf(':');
        String code = colon > 0 ? message.substring(0, colon) : message;
        return code.matches("[A-Z0-9_]{3,80}") ? code : fallback;
    }

    private Result retry(UUID jobId, String reasonCode) {
        tx.executeWithoutResult(status -> jobs.findById(jobId).orElseThrow()
                .scheduleRetry(reasonCode, clock.instant().plus(RETRY_DELAY)));
        return new Result(jobId, Outcome.RETRY_SCHEDULED, reasonCode);
    }

    // ---- Part 5: receipt polling ------------------------------------------------------------------

    static final Duration REBROADCAST_AFTER = Duration.ofMinutes(2);
    static final Duration RECEIPT_POLL_DELAY = Duration.ofSeconds(5);
    static final Duration REBROADCAST_RETRY_DELAY = Duration.ofSeconds(30);
    static final int MAX_REBROADCASTS = 5;
    static final int POLL_BATCH = 10;

    /** Outcome of one receipt check (no secrets). */
    public enum PollOutcome { CONFIRMED, PENDING, REBROADCAST, FAILED }

    public record PollResult(UUID jobId, PollOutcome outcome, String reasonCode) {
    }

    private record PollItem(UUID jobId, UUID attemptId, String txHash, Instant submittedAt, int retryCount,
            CaseVerifiedReceiptVerifier.Expected expected) {
    }

    /**
     * Checks receipts of SUBMITTED CASE_VERIFIED jobs. A job becomes CONFIRMED only when the receipt and its
     * single CaseVerified log match every field prepared in the database (BR-10); RPC runs outside any SQL
     * transaction, and the job + attempt update for each verdict is one short transaction.
     */
    public List<PollResult> pollSubmitted() {
        Instant now = clock.instant();
        List<PollResult> results = new ArrayList<>();
        List<PollItem> items = tx.execute(status -> lockSubmitted(now, results));
        for (PollItem item : items) {
            results.add(checkReceipt(item));
        }
        return results;
    }

    private List<PollItem> lockSubmitted(Instant now, List<PollResult> results) {
        List<PollItem> items = new ArrayList<>();
        for (BlockchainJob job : jobs.lockSubmittedCaseVerifiedJobs(properties.chainId(), now, POLL_BATCH)) {
            Optional<BlockchainTransaction> attempt = attempts.findByJob_IdOrderByPreparedAtAsc(job.getId()).stream()
                    .filter(a -> BlockchainTransaction.SUBMITTED.equals(a.getStatus()))
                    .max(Comparator.comparing(BlockchainTransaction::getSubmittedAt));
            Optional<CaseVerifiedSource> source = jobs.findCaseVerifiedSource(job.getId());
            if (attempt.isEmpty() || source.isEmpty()) {
                String code = attempt.isEmpty() ? "SUBMITTED_WITHOUT_ATTEMPT" : "CASE_VERIFIED_SOURCE_MISSING";
                job.markFailed(code);
                results.add(new PollResult(job.getId(), PollOutcome.FAILED, code));
                continue;
            }
            CaseVerifiedEnvelope envelope = new CaseVerifiedEnvelope(source.get().getOrgPublicRef(),
                    source.get().getCasePublicRef(), job.getLogicalEventId(), job.getChainSequence(),
                    job.getEvidenceCommitment(), job.getSchemaVersion());
            BlockchainTransaction submitted = attempt.get();
            job.leaseForPolling(now.plus(LEASE));
            items.add(new PollItem(job.getId(), submitted.getId(), submitted.getTxHash(), submitted.getSubmittedAt(),
                    job.getRetryCount(), new CaseVerifiedReceiptVerifier.Expected(submitted.getTxHash(),
                            submitted.getContractAddress(), submitted.getSignerAddress(), envelope)));
        }
        return items;
    }

    private PollResult checkReceipt(PollItem item) {
        Optional<ChainReceipt> receipt;
        Optional<String> canonical = Optional.empty();
        try {
            receipt = adapter.getReceipt(item.txHash());
            if (receipt.isPresent() && receipt.get().blockNumber() != null) {
                canonical = adapter.canonicalBlockHash(receipt.get().blockNumber());
            }
        } catch (BlockchainRpcException e) {
            deferWithRetry(item, e.code(), RECEIPT_POLL_DELAY);
            return new PollResult(item.jobId(), PollOutcome.PENDING, e.code());
        }

        if (receipt.isEmpty()) {
            return handleMissingReceipt(item);
        }

        CaseVerifiedReceiptVerifier.Verdict verdict = CaseVerifiedReceiptVerifier.verify(item.expected(), receipt.get(), canonical);
        if (verdict instanceof CaseVerifiedReceiptVerifier.Confirmed confirmed) {
            String receiptJson = CanonicalJson.toCanonicalString(Map.of(
                    "blockHash", confirmed.blockHash().toLowerCase(),
                    "blockNumber", confirmed.blockNumber(),
                    "blockTimestamp", confirmed.blockTimestamp(),
                    "logIndex", Math.max(confirmed.logIndex(), 0),
                    "status", 1,
                    "transactionHash", item.txHash().toLowerCase()));
            tx.executeWithoutResult(status -> {
                attempts.findById(item.attemptId()).orElseThrow()
                        .markConfirmed(confirmed.blockNumber(), receiptJson, clock.instant());
                jobs.findById(item.jobId()).orElseThrow().markConfirmed();
            });
            return new PollResult(item.jobId(), PollOutcome.CONFIRMED, null);
        }
        if (verdict instanceof CaseVerifiedReceiptVerifier.Reverted reverted) {
            String receiptJson = CanonicalJson.toCanonicalString(Map.of(
                    "blockHash", reverted.blockHash().toLowerCase(),
                    "blockNumber", reverted.blockNumber(),
                    "status", 0,
                    "transactionHash", item.txHash().toLowerCase()));
            tx.executeWithoutResult(status -> {
                attempts.findById(item.attemptId()).orElseThrow()
                        .markReverted(reverted.blockNumber(), receiptJson, "TX_REVERTED");
                jobs.findById(item.jobId()).orElseThrow().markFailed("TX_REVERTED");
            });
            return new PollResult(item.jobId(), PollOutcome.FAILED, "TX_REVERTED");
        }
        CaseVerifiedReceiptVerifier.Mismatch mismatch = (CaseVerifiedReceiptVerifier.Mismatch) verdict;
        if (mismatch.transientCondition()) {
            tx.executeWithoutResult(status -> jobs.findById(item.jobId()).orElseThrow()
                    .deferPolling(clock.instant().plus(RECEIPT_POLL_DELAY)));
            return new PollResult(item.jobId(), PollOutcome.PENDING, mismatch.reasonCode());
        }
        tx.executeWithoutResult(status -> {
            attempts.findById(item.attemptId()).orElseThrow().recordError(mismatch.reasonCode());
            jobs.findById(item.jobId()).orElseThrow().markFailed(mismatch.reasonCode());
        });
        return new PollResult(item.jobId(), PollOutcome.FAILED, mismatch.reasonCode());
    }

    /** Not mined yet: wait; after REBROADCAST_AFTER re-send the same persisted bytes (never re-sign). */
    private PollResult handleMissingReceipt(PollItem item) {
        Instant now = clock.instant();
        if (Duration.between(item.submittedAt(), now).compareTo(REBROADCAST_AFTER) < 0) {
            tx.executeWithoutResult(status -> jobs.findById(item.jobId()).orElseThrow()
                    .deferPolling(now.plus(RECEIPT_POLL_DELAY)));
            return new PollResult(item.jobId(), PollOutcome.PENDING, "RECEIPT_PENDING");
        }
        if (item.retryCount() >= MAX_REBROADCASTS) {
            // A retry budget is not proof of a reverted transaction. Stop automatic rebroadcast,
            // retain the original attempt and keep polling so a late receipt can still reconcile.
            // retryCount also includes RPC/binding errors; do not interpret it as a mining verdict.
            String code = "RECEIPT_PENDING_OPERATOR_ATTENTION";
            tx.executeWithoutResult(status -> jobs.findById(item.jobId()).orElseThrow()
                    .scheduleRetry(code, now.plus(REBROADCAST_RETRY_DELAY)));
            return new PollResult(item.jobId(), PollOutcome.PENDING, code);
        }
        BlockchainTransaction attempt = attempts.findById(item.attemptId()).orElseThrow();
        String raw = Numeric.toHexString(cipher.decrypt(attempt.getSignedTxCiphertext(),
                JobSecretCipher.associatedData(item.jobId(), "signed-tx")));
        String code = "REBROADCAST";
        try {
            adapter.broadcastRawTransaction(raw);
        } catch (BlockchainRpcException e) {
            code = e.code(); // e.g. "already known": the node still holds the transaction
        }
        deferWithRetry(item, code, REBROADCAST_RETRY_DELAY);
        return new PollResult(item.jobId(), PollOutcome.REBROADCAST, code);
    }

    private void deferWithRetry(PollItem item, String reasonCode, Duration delay) {
        tx.executeWithoutResult(status -> jobs.findById(item.jobId()).orElseThrow()
                .scheduleRetry(reasonCode, clock.instant().plus(delay)));
    }
}
