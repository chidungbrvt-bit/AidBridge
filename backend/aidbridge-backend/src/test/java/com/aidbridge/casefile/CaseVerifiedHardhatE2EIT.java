package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.Web3jBlockchainAdapter;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.blockchain.worker.CaseVerifiedManifestBuilder;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import com.aidbridge.verification.service.VerificationOutboxService;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * C05 end-to-end on a real local chain: deploys the reviewed C03 artifact (the bytecode verified on Fuji) to a
 * Hardhat node, registers the DB organization's public_ref with a dedicated relayer, lets the real Web3j worker
 * send a B05 CASE_VERIFIED job, confirms it from the receipt, then reads the contract state back and compares the
 * contract-computed payloadDigest with the Java-computed one.
 *
 * <p>Runs only when AIDBRIDGE_E2E_HARDHAT_RPC_URL points at localhost and the node reports chainId 31337.
 * Start the node with {@code cd contracts && npx hardhat node}.
 */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_E2E_HARDHAT_RPC_URL", matches = "http://(127\\.0\\.0\\.1|localhost):\\d+/?")
class CaseVerifiedHardhatE2EIT extends AbstractCaseHttpIT {

    // Public Hardhat development accounts #0 (admin/deployer) and #1 (relayer). Documented test keys only.
    private static final String ADMIN_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String RELAYER_KEY = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
    private static final long LOCAL_CHAIN_ID = 31337L;
    private static final Path REVIEWED_ARTIFACT = Path.of("../../contracts/deployment/AidRegistry.c03-reviewed.artifact.json");

    @Autowired VerificationOutboxService outbox;
    @Autowired BlockchainJobRepository jobs;
    @Autowired BlockchainTransactionRepository attempts;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void caseVerifiedJobIsSentConfirmedAndMatchesTheContractState() throws Exception {
        String rpcUrl = System.getenv("AIDBRIDGE_E2E_HARDHAT_RPC_URL");
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        try {
            assertThat(web3j.ethChainId().send().getChainId().longValueExact())
                    .as("E2E must only ever run against a local Hardhat chain").isEqualTo(LOCAL_CHAIN_ID);
            Credentials admin = Credentials.create(ADMIN_KEY);
            Credentials relayer = Credentials.create(RELAYER_KEY);

            // 1. Deploy the reviewed C03 bytecode and register the DB organization with a dedicated relayer.
            String registry = deploy(web3j, admin);
            byte[] orgRef = jdbc.queryForObject("SELECT public_ref FROM organizations WHERE id = ?", byte[].class, ORG_A);
            send(web3j, admin, registry, FunctionEncoder.encode(new Function("registerOrganization",
                    Arrays.<Type>asList(new Bytes32(orgRef), new Address(relayer.getAddress())), List.of())), BigInteger.valueOf(300_000));

            jdbc.update("UPDATE blockchain_jobs SET next_retry_at = now() + interval '1 day' WHERE chain_id = 31337 AND status IN ('PENDING','PROCESSING','SUBMITTED')");
            jdbc.update("UPDATE organizations SET relayer_address = ? WHERE id = ?", relayer.getAddress().toLowerCase(), ORG_A);
            UUID jobId = newLocalCaseVerifiedJob();

            // 2. Real adapter + worker: prepare, persist, broadcast, then confirm from the receipt.
            BlockchainProperties properties = new BlockchainProperties(true, rpcUrl, LOCAL_CHAIN_ID, registry, RELAYER_KEY, null, null);
            properties.validate();
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            CaseVerifiedWorker worker = new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                    new CaseVerifiedManifestBuilder(json), new Web3jBlockchainAdapter(web3j, properties),
                    new JobSecretCipher(key), properties, Clock.systemUTC());

            var submitted = worker.processNextJob().orElseThrow();
            assertThat(submitted.jobId()).isEqualTo(jobId);
            assertThat(submitted.outcome()).isEqualTo(CaseVerifiedWorker.Outcome.SUBMITTED);
            assertThat(submitted.reasonCode()).isNull();
            String txHash = jdbc.queryForObject("SELECT tx_hash FROM blockchain_transactions WHERE blockchain_job_id = ?", String.class, jobId);
            waitForReceipt(web3j, txHash);

            var polled = worker.pollSubmitted().stream().filter(r -> r.jobId().equals(jobId)).findFirst().orElseThrow();
            assertThat(polled.outcome()).isEqualTo(CaseVerifiedWorker.PollOutcome.CONFIRMED);

            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT j.status, j.logical_event_id, j.chain_sequence, j.evidence_commitment, o.public_ref AS org_ref,
                           c.public_ref AS case_ref, t.status AS tx_status, t.block_number, t.signer_address, t.contract_address
                    FROM blockchain_jobs j
                    JOIN audit_events a ON a.id = j.audit_event_id
                    JOIN organizations o ON o.id = a.organization_id
                    JOIN aid_cases c ON c.id = a.aid_case_id
                    JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                    WHERE j.id = ?
                    """, jobId);
            assertThat(row).containsEntry("status", "CONFIRMED").containsEntry("tx_status", "CONFIRMED")
                    .containsEntry("signer_address", relayer.getAddress().toLowerCase())
                    .containsEntry("contract_address", registry);
            assertThat(row.get("block_number")).isNotNull();

            // 3. Read the contract back: stored record and case summary must match the DB and the Java digest.
            CaseVerifiedEnvelope envelope = new CaseVerifiedEnvelope((byte[]) row.get("org_ref"), (byte[]) row.get("case_ref"),
                    (byte[]) row.get("logical_event_id"), (Long) row.get("chain_sequence"), (byte[]) row.get("evidence_commitment"), 1);
            List<Type> record = call(web3j, registry, new Function("getEventRecord",
                    Arrays.<Type>asList(new Bytes32(envelope.eventId())),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() { }, new TypeReference<Bytes4>() { },
                            new TypeReference<Bytes32>() { }, new TypeReference<Bytes32>() { }, new TypeReference<Uint64>() { },
                            new TypeReference<Bytes32>() { }, new TypeReference<Uint16>() { }, new TypeReference<Bytes32>() { })));
            assertThat(((Bool) record.get(0)).getValue()).isTrue();
            assertThat(((Bytes4) record.get(1)).getValue()).isEqualTo(AidRegistryAbi.RECORD_CASE_VERIFICATION_SELECTOR);
            assertThat(((Bytes32) record.get(2)).getValue()).isEqualTo(envelope.orgRef());
            assertThat(((Bytes32) record.get(3)).getValue()).isEqualTo(envelope.caseRef());
            assertThat(((Uint64) record.get(4)).getValue()).isEqualTo(BigInteger.ONE);
            assertThat(((Bytes32) record.get(5)).getValue()).isEqualTo(envelope.evidenceCommitment());
            assertThat(((Uint16) record.get(6)).getValue()).isEqualTo(BigInteger.ONE);
            assertThat(((Bytes32) record.get(7)).getValue())
                    .as("payloadDigest computed by the contract must equal the Java computation")
                    .isEqualTo(envelope.payloadDigest());

            List<Type> summary = call(web3j, registry, new Function("getCaseSummary",
                    Arrays.<Type>asList(new Bytes32(envelope.caseRef())),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() { }, new TypeReference<Uint256>() { },
                            new TypeReference<Uint64>() { })));
            assertThat(((Bool) summary.get(0)).getValue()).isTrue();
            assertThat(((Uint256) summary.get(1)).getValue()).isEqualTo(BigInteger.ZERO);
            assertThat(((Uint64) summary.get(2)).getValue()).isEqualTo(BigInteger.ONE);
        } finally {
            web3j.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    private String deploy(Web3j web3j, Credentials admin) throws Exception {
        Map<String, Object> artifact = json.readValue(Files.readString(REVIEWED_ARTIFACT), Map.class);
        String creation = (String) artifact.get("bytecode") + TypeEncoder.encode(new Address(admin.getAddress()));
        TransactionReceipt receipt = send(web3j, admin, null, creation, BigInteger.valueOf(3_000_000));
        return receipt.getContractAddress().toLowerCase();
    }

    private TransactionReceipt send(Web3j web3j, Credentials from, String to, String data, BigInteger gasLimit) throws Exception {
        BigInteger nonce = web3j.ethGetTransactionCount(from.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.TWO);
        RawTransaction raw = to == null
                ? RawTransaction.createContractTransaction(nonce, gasPrice, gasLimit, BigInteger.ZERO, data)
                : RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, BigInteger.ZERO, data);
        EthSendTransaction sent = web3j.ethSendRawTransaction(
                Numeric.toHexString(TransactionEncoder.signMessage(raw, LOCAL_CHAIN_ID, from))).send();
        assertThat(sent.hasError()).as(sent.hasError() ? sent.getError().getMessage() : "").isFalse();
        TransactionReceipt receipt = waitForReceipt(web3j, sent.getTransactionHash());
        assertThat(receipt.getStatus()).isEqualTo("0x1");
        return receipt;
    }

    private static TransactionReceipt waitForReceipt(Web3j web3j, String hash) throws Exception {
        for (int i = 0; i < 50; i++) {
            Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.get();
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No receipt for " + hash + " (is `npx hardhat node` running with automine?)");
    }

    @SuppressWarnings("rawtypes")
    private static List<Type> call(Web3j web3j, String to, Function function) throws Exception {
        String result = web3j.ethCall(Transaction.createEthCallTransaction(null, to, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.LATEST).send().getValue();
        return FunctionReturnDecoder.decode(result, function.getOutputParameters());
    }

    private UUID newLocalCaseVerifiedJob() throws Exception {
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
        UUID jobId = jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, auditEventId);
        jdbc.update("UPDATE blockchain_jobs SET chain_id = 31337 WHERE id = ?", jobId);
        return jobId;
    }
}
