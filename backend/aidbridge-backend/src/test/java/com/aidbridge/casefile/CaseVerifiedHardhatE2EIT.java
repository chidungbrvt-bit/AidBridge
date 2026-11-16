package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.Web3jBlockchainAdapter;
import com.aidbridge.blockchain.crypto.JobSecretCipher;
import com.aidbridge.blockchain.envelope.AidRegistryAbi;
import com.aidbridge.blockchain.envelope.CaseVerifiedEnvelope;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.blockchain.repository.BlockchainTransactionRepository;
import com.aidbridge.blockchain.worker.CaseVerifiedManifestBuilder;
import com.aidbridge.blockchain.worker.CaseVerifiedWorker;
import com.aidbridge.blockchain.worker.SignerSessionLock;
import com.aidbridge.verification.service.VerificationOutboxService;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/**
 * C05 end-to-end on a real local chain: deploys the reviewed C03 artifact (the bytecode verified on Fuji) to a
 * Hardhat node and exercises the B05 CASE_VERIFIED worker. The separate C06 test deploys the pinned C06
 * artifact and verifies all five milestones against PostgreSQL and independent chain readback.
 *
 * <p>Runs only when AIDBRIDGE_E2E_HARDHAT_RPC_URL points at localhost and the node reports chainId 31337.
 * Start the node with {@code cd contracts && npx hardhat node}.
 */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_E2E_HARDHAT_RPC_URL", matches = "http://(127\\.0\\.0\\.1|localhost):\\d+/?")
class CaseVerifiedHardhatE2EIT extends AbstractCampaignHttpIT {

    // Public Hardhat development accounts #0 (admin/deployer) and #1 (relayer). Documented test keys only.
    private static final String ADMIN_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String RELAYER_KEY = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
    private static final long LOCAL_CHAIN_ID = 31337L;
    private static final Path REVIEWED_ARTIFACT = Path.of("../../contracts/deployment/AidRegistry.c03-reviewed.artifact.json");
    private static final Path C06_ARTIFACT = Path.of("../../contracts/artifacts/contracts/AidRegistry.sol/AidRegistry.json");
    private static final String C06_BUILD_ID = "solc-0_8_28-6d69e2cf1ddbaa7f61c90daac742b8efd4cf518a";
    private static final String C06_ARTIFACT_SHA256 = "976825c365d0dd99c2097e90b0bbced2d36556524fcb785de4ac19367499c592";
    private static final String C06_RUNTIME_HASH = "0x525314d10311e01e01a61a6f9e75ee432f59ca09ff3aa27445aed3962e09011e";

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
                    new JobSecretCipher(key), properties, Clock.systemUTC(), new SignerSessionLock(jdbc.getDataSource()));

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

    @Test
    void c06ConfirmsFiveGoodsMilestonesInOrderAndReusesThePreparedAttempt() throws Exception {
        runC06GoodsScenario(false);
    }

    @Test
    void c06ConfirmsMultipleCommitmentsAndPartialReceiptsInPersistedOrder() throws Exception {
        runC06GoodsScenario(true);
    }

    private void runC06GoodsScenario(boolean multipleCommitments) throws Exception {
        String rpcUrl = System.getenv("AIDBRIDGE_E2E_HARDHAT_RPC_URL");
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));
        try {
            assertThat(web3j.ethChainId().send().getChainId().longValueExact()).isEqualTo(LOCAL_CHAIN_ID);
            Credentials admin = Credentials.create(ADMIN_KEY);
            Credentials relayer = Credentials.create(RELAYER_KEY);
            String registry = deployC06(web3j, admin);
            byte[] orgRef = jdbc.queryForObject("SELECT public_ref FROM organizations WHERE id = ?", byte[].class, ORG_A);
            send(web3j, admin, registry, FunctionEncoder.encode(new Function("registerOrganization",
                    Arrays.<Type>asList(new Bytes32(orgRef), new Address(relayer.getAddress())), List.of())),
                    BigInteger.valueOf(300_000));
            assertThat(jdbc.update("UPDATE organizations SET relayer_address = ? WHERE id = ?",
                    relayer.getAddress().toLowerCase(Locale.ROOT), ORG_A)).isEqualTo(1);

            GoodsScenario scenario = newGoodsScenario(multipleCommitments);
            int expectedJobCount = multipleCommitments ? 8 : 5;
            assertThat(scenario.milestones()).hasSize(expectedJobCount);
            var before = stock(scenario);
            assertThat(before).hasSize(2);
            assertThat(before.get(0)).containsEntry("pledged", 8).containsEntry("cancelled", 2)
                    .containsEntry("received", 5).containsEntry("delivered", 4);
            assertThat(before.get(1)).containsEntry("pledged", 5).containsEntry("cancelled", 1)
                    .containsEntry("received", 3).containsEntry("delivered", 2);
            jdbc.update("""
                    UPDATE blockchain_jobs SET next_retry_at = now() + interval '1 day'
                    WHERE chain_id = 31337 AND status IN ('PENDING', 'PROCESSING', 'SUBMITTED') AND aid_case_id <> ?
                    """, scenario.caseId());
            assertThat(jdbc.update("""
                    UPDATE blockchain_jobs j SET chain_id = 31337
                    WHERE j.aid_case_id = ? AND j.status = 'PENDING' AND j.chain_id = 43113
                      AND NOT EXISTS (SELECT 1 FROM blockchain_transactions t WHERE t.blockchain_job_id = j.id)
                    """, scenario.caseId())).isEqualTo(expectedJobCount);

            BlockchainProperties properties = new BlockchainProperties(true, rpcUrl, LOCAL_CHAIN_ID, registry, RELAYER_KEY, null, null);
            properties.validate();
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            JobSecretCipher cipher = new JobSecretCipher(key);
            CaseVerifiedWorker worker = worker(new Web3jBlockchainAdapter(web3j, properties), cipher, properties);
            System.out.printf("C06_E2E artifact=%s sha256=%s registry=%s chainId=%d expectedJobs=%d%n",
                    C06_BUILD_ID, C06_ARTIFACT_SHA256, registry, LOCAL_CHAIN_ID, expectedJobCount);

            for (int index = 0; index < scenario.milestones().size(); index++) {
                Milestone expected = scenario.milestones().get(index);
                long sequence = index + 1L;
                if ("COMMITMENT_REDUCED".equals(expected.eventType())) {
                    worker = restartBeforeBroadcast(web3j, properties, cipher, expected.jobId());
                } else {
                    assertThat(worker.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                            expected.jobId(), CaseVerifiedWorker.Outcome.SUBMITTED, null));
                }
                String hash = jdbc.queryForObject("SELECT tx_hash FROM blockchain_transactions WHERE blockchain_job_id = ?",
                        String.class, expected.jobId());
                TransactionReceipt receipt = waitForReceipt(web3j, hash);
                assertThat(worker.pollSubmitted()).containsExactly(new CaseVerifiedWorker.PollResult(
                        expected.jobId(), CaseVerifiedWorker.PollOutcome.CONFIRMED, null));
                assertMilestone(web3j, registry, relayer.getAddress(), expected, sequence, receipt);
            }

            assertThat(worker.processNextJob()).isEmpty();
            assertThat(worker.pollSubmitted()).isEmpty();
            assertThat(stock(scenario)).isEqualTo(before);
            assertThat(count("blockchain_jobs", "aid_case_id", scenario.caseId())).isEqualTo(expectedJobCount);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type IN (
                        'CASE_VERIFIED', 'AID_PLEDGED', 'COMMITMENT_REDUCED', 'GOODS_RECEIVED', 'DELIVERY_RECORDED')
                    """, Long.class, scenario.caseId())).isEqualTo((long) expectedJobCount);
            assertThat(count("contributions", "campaign_id", scenario.campaignId())).isEqualTo(multipleCommitments ? 2 : 1);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM goods_receipts r JOIN contributions x ON x.id = r.contribution_id
                    WHERE x.campaign_id = ?
                    """, Long.class, scenario.campaignId())).isEqualTo(multipleCommitments ? 3L : 1L);
            byte[] caseRef = jdbc.queryForObject("SELECT public_ref FROM aid_cases WHERE id = ?", byte[].class, scenario.caseId());
            List<Type> summary = call(web3j, registry, new Function("getCaseSummary",
                    Arrays.<Type>asList(new Bytes32(caseRef)),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() { }, new TypeReference<Uint256>() { },
                            new TypeReference<Uint64>() { })));
            assertThat(((Bool) summary.get(0)).getValue()).isTrue();
            assertThat(((Uint256) summary.get(1)).getValue()).isEqualTo(BigInteger.ZERO);
            assertThat(((Uint64) summary.get(2)).getValue()).isEqualTo(BigInteger.valueOf(expectedJobCount));
            System.out.printf("C06_E2E PASS confirmedJobs=%d one attempt per job, restart and stock checks%n", expectedJobCount);
        } finally {
            web3j.shutdown();
        }
    }

    private record Milestone(UUID jobId, String eventType, String functionName, String logName, List<String> refs) {
    }

    private record GoodsScenario(UUID caseId, UUID campaignId, UUID deliveryId,
            List<Milestone> milestones) {
    }

    private record Pledge(UUID contributionId, List<UUID> lineIds, String commitmentRef, Milestone milestone) {
    }

    private GoodsScenario newGoodsScenario(boolean multipleCommitments) throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin, List.of(Map.of("name", "Rice", "unit", "BAG", "quantity", 10),
                Map.of("name", "Water", "unit", "BOX", "quantity", 6)));
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        List<UUID> items = campaignItemIds(campaignId);
        UUID verified = jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE aid_case_id = ? AND chain_sequence = 1",
                UUID.class, caseId);
        List<Milestone> milestones = new ArrayList<>();
        milestones.add(new Milestone(verified, "CASE_VERIFIED", "recordCaseVerification", "CaseVerified", List.of()));
        Pledge primary = newPledge(donor, campaignId, items, multipleCommitments ? 6 : 8, multipleCommitments ? 4 : 5);
        milestones.add(primary.milestone());
        Pledge secondary = multipleCommitments ? newPledge(donor(true), campaignId, items, 2, 1) : null;
        if (secondary != null) {
            assertThat(secondary.commitmentRef()).isNotEqualTo(primary.commitmentRef());
            milestones.add(secondary.milestone());
        }

        String path = "/api/commitments/" + primary.contributionId();
        var reductionResponse = donor.browser().send("POST", path + "/cancellations", Map.of(
                "reason", "Partial stock adjustment", "items", List.of(
                        item("contributionItemId", primary.lineIds().get(0), 2),
                        item("contributionItemId", primary.lineIds().get(1), 1))), newKey(), true);
        status(reductionResponse, 200);
        milestones.add(new Milestone(jobIdForAudit(node(reductionResponse).path("auditEventId").asText()),
                "COMMITMENT_REDUCED", "recordCommitmentReduction", "CommitmentReduced", List.of(primary.commitmentRef())));
        if (secondary == null) {
            milestones.add(receive(orgAdmin, caseId, primary, 5, 3));
        } else {
            Milestone first = receive(orgAdmin, caseId, primary, 1, 1);
            Milestone middle = receive(orgAdmin, caseId, secondary, 1, 1);
            Milestone last = receive(orgAdmin, caseId, primary, 3, 1);
            assertThat(List.of(first.refs().getFirst(), middle.refs().getFirst(), last.refs().getFirst())).doesNotHaveDuplicates();
            milestones.addAll(List.of(first, middle, last));
        }
        List<UUID> evidenceIds = jdbc.queryForList("""
                SELECT DISTINCT evidence_version_id FROM case_evidence_links WHERE aid_case_id = ? ORDER BY evidence_version_id
                """, UUID.class, caseId);
        var deliveryResponse = volunteer.browser().send("POST", "/api/cases/" + caseId + "/deliveries", Map.of(
                "outcome", "DELIVERED", "handedOverAt", Instant.now().toString(), "evidenceVersionIds", evidenceIds,
                "items", List.of(item("campaignItemId", items.get(0), 4), item("campaignItemId", items.get(1), 2))), newKey(), true);
        status(deliveryResponse, 201);
        var delivery = node(deliveryResponse);
        assertThat(delivery.path("reviewStatus").asText()).isEqualTo("PENDING");
        UUID deliveryId = UUID.fromString(delivery.path("id").asText());

        String deliveryRef = Numeric.toHexString(jdbc.queryForObject(
                "SELECT delivery_ref FROM deliveries WHERE id = ?", byte[].class, deliveryId));
        UUID deliveryAuditId = UUID.fromString(delivery.path("auditEventId").asText());
        assertThat(jdbc.queryForObject("SELECT payload_private ->> 'deliveryRef' FROM audit_events WHERE id = ?",
                String.class, deliveryAuditId)).isEqualTo(deliveryRef);
        milestones.add(new Milestone(jobIdForAudit(deliveryAuditId.toString()), "DELIVERY_RECORDED",
                "recordDelivery", "DeliveryRecorded", List.of(deliveryRef)));
        return new GoodsScenario(caseId, campaignId, deliveryId, List.copyOf(milestones));
    }

    private Pledge newPledge(Actor donor, UUID campaignId, List<UUID> items, int rice, int water) throws Exception {
        var response = donor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments", Map.of("items", List.of(
                item("campaignItemId", items.get(0), rice), item("campaignItemId", items.get(1), water))), newKey(), true);
        status(response, 201);
        var pledge = node(response);
        UUID contributionId = UUID.fromString(pledge.path("id").asText());
        List<UUID> lines = items.stream().map(itemId -> jdbc.queryForObject("""
                SELECT id FROM contribution_items WHERE contribution_id = ? AND campaign_item_id = ?
                """, UUID.class, contributionId, itemId)).toList();
        String commitmentRef = Numeric.toHexString(jdbc.queryForObject(
                "SELECT public_ref FROM contributions WHERE id = ? AND campaign_id = ?", byte[].class, contributionId, campaignId));
        return new Pledge(contributionId, lines, commitmentRef, new Milestone(jobIdForAudit(pledge.path("auditEventId").asText()),
                "AID_PLEDGED", "recordAidCommitment", "AidCommitted", List.of(commitmentRef)));
    }

    private Milestone receive(Actor orgAdmin, UUID caseId, Pledge pledge, int rice, int water) throws Exception {
        var response = orgAdmin.browser().send("POST", "/api/commitments/" + pledge.contributionId() + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", List.of(item("contributionItemId", pledge.lineIds().get(0), rice),
                        item("contributionItemId", pledge.lineIds().get(1), water))), newKey(), true);
        status(response, 201);
        var receipt = node(response);
        UUID auditId = UUID.fromString(receipt.path("auditEventId").asText());
        var audit = jdbc.queryForMap("""
                SELECT a.payload_private ->> 'receiptRef' AS receipt_ref,
                       a.payload_private ->> 'commitmentRef' AS commitment_ref, r.contribution_id
                FROM audit_events a JOIN goods_receipts r ON r.id = a.entity_id
                WHERE a.id = ? AND r.id = ? AND a.aid_case_id = ? AND a.organization_id = ?
                """, auditId, UUID.fromString(receipt.path("id").asText()), caseId, ORG_A);
        assertThat(audit).containsEntry("contribution_id", pledge.contributionId()).containsEntry("commitment_ref", pledge.commitmentRef());
        String receiptRef = (String) audit.get("receipt_ref");
        assertThat(receiptRef).matches("0x[0-9a-f]{64}");
        return new Milestone(jobIdForAudit(auditId.toString()), "GOODS_RECEIVED", "recordGoodsReceipt", "GoodsReceived",
                List.of(receiptRef, pledge.commitmentRef()));
    }

    private static Map<String, Object> item(String idField, UUID id, int quantity) {
        return Map.of(idField, id, "quantity", quantity);
    }

    private UUID jobIdForAudit(String auditId) {
        return jdbc.queryForObject("SELECT id FROM blockchain_jobs WHERE audit_event_id = ?", UUID.class, UUID.fromString(auditId));
    }

    private List<Map<String, Object>> stock(GoodsScenario scenario) {
        return jdbc.queryForList("""
                SELECT ci.id AS campaign_item_id,
                       CAST((SELECT sum(i.pledged_quantity) FROM contribution_items i WHERE i.campaign_item_id = ci.id) AS int) AS pledged,
                       CAST((SELECT sum(i.cancelled_quantity) FROM contribution_items i WHERE i.campaign_item_id = ci.id) AS int) AS cancelled,
                       CAST((SELECT sum(r.received_quantity) FROM goods_receipt_items r JOIN contribution_items i
                             ON i.id = r.contribution_item_id WHERE i.campaign_item_id = ci.id) AS int) AS received,
                       d.delivered_quantity AS delivered
                FROM campaign_items ci LEFT JOIN delivery_items d ON d.campaign_item_id = ci.id AND d.delivery_id = ?
                WHERE ci.campaign_id = ? ORDER BY lower(ci.item_name), lower(ci.unit), ci.id
                """, scenario.deliveryId(), scenario.campaignId());
    }

    private CaseVerifiedWorker worker(Web3jBlockchainAdapter adapter, JobSecretCipher cipher,
            BlockchainProperties properties) {
        return new CaseVerifiedWorker(new TransactionTemplate(transactionManager), jobs, attempts,
                new CaseVerifiedManifestBuilder(json), adapter, cipher, properties,
                Clock.systemUTC(), new SignerSessionLock(jdbc.getDataSource()));
    }

    private CaseVerifiedWorker restartBeforeBroadcast(Web3j web3j, BlockchainProperties properties,
            JobSecretCipher cipher, UUID jobId) throws Exception {
        AtomicReference<String> interruptedBytes = new AtomicReference<>();
        Web3jBlockchainAdapter interruptedAdapter = new Web3jBlockchainAdapter(web3j, properties) {
            @Override
            public String broadcastRawTransaction(String rawTransactionHex) {
                interruptedBytes.set(rawTransactionHex);
                throw new IllegalStateException("TEST_STOP_BEFORE_BROADCAST");
            }
        };
        CaseVerifiedWorker interrupted = worker(interruptedAdapter, cipher, properties);
        assertThatThrownBy(interrupted::processNextJob).hasMessage("TEST_STOP_BEFORE_BROADCAST");
        assertThat(interruptedBytes.get()).isNotBlank();
        var original = attemptSnapshot(jobId);
        assertThat(original).containsEntry("status", "PREPARED").containsEntry("job_status", "PROCESSING");
        assertThat(web3j.ethGetTransactionByHash((String) original.get("tx_hash")).send().getTransaction()).isEmpty();
        assertThat(jdbc.update("UPDATE blockchain_jobs SET lease_until = now() - interval '1 second' WHERE id = ?",
                jobId)).isEqualTo(1);

        Web3jBlockchainAdapter resumedAdapter = new Web3jBlockchainAdapter(web3j, properties) {
            @Override
            public String broadcastRawTransaction(String rawTransactionHex) {
                String prepared = interruptedBytes.getAndSet(null);
                if (prepared != null) {
                    assertThat(rawTransactionHex).isEqualTo(prepared);
                }
                return super.broadcastRawTransaction(rawTransactionHex);
            }
        };
        CaseVerifiedWorker resumed = worker(resumedAdapter, cipher, properties);
        assertThat(resumed.processNextJob().orElseThrow()).isEqualTo(new CaseVerifiedWorker.Result(
                jobId, CaseVerifiedWorker.Outcome.REBROADCAST, null));
        assertThat(interruptedBytes.get()).isNull();
        var recovered = attemptSnapshot(jobId);
        assertThat(recovered).containsEntry("status", "SUBMITTED").containsEntry("job_status", "SUBMITTED")
                .containsEntry("id", original.get("id")).containsEntry("tx_hash", original.get("tx_hash"))
                .containsEntry("nonce", original.get("nonce")).containsEntry("event_id", original.get("event_id"));
        assertThat((byte[]) recovered.get("signed_tx_ciphertext")).isEqualTo((byte[]) original.get("signed_tx_ciphertext"));
        assertThat(count("blockchain_transactions", "blockchain_job_id", jobId)).isEqualTo(1);
        System.out.printf("C06_E2E_RESTART eventId=%s txHash=%s nonce=%s signedBytesReused=true%n",
                original.get("event_id"), original.get("tx_hash"), original.get("nonce"));
        return resumed;
    }

    private Map<String, Object> attemptSnapshot(UUID jobId) {
        return jdbc.queryForMap("""
                SELECT t.id, t.status, t.tx_hash, t.nonce, t.signed_tx_ciphertext, j.status AS job_status,
                       '0x' || encode(j.logical_event_id, 'hex') AS event_id
                FROM blockchain_transactions t JOIN blockchain_jobs j ON j.id = t.blockchain_job_id
                WHERE j.id = ?
                """, jobId);
    }

    private void assertMilestone(Web3j web3j, String registry, String signer, Milestone expected, long sequence,
            TransactionReceipt receipt) throws Exception {
        var row = jdbc.queryForMap("""
                SELECT j.status, j.chain_id, j.logical_event_id, j.chain_sequence, j.evidence_commitment, j.schema_version,
                       a.event_type, o.public_ref AS org_ref, c.public_ref AS case_ref, t.status AS attempt_status,
                       t.chain_id AS attempt_chain_id, t.tx_hash, t.block_number, t.signer_address, t.contract_address,
                       t.nonce, t.receipt_status, CAST(t.receipt_private AS text) AS receipt_private
                FROM blockchain_jobs j
                JOIN audit_events a ON a.id = j.audit_event_id
                JOIN organizations o ON o.id = a.organization_id
                JOIN aid_cases c ON c.id = j.aid_case_id
                JOIN blockchain_transactions t ON t.blockchain_job_id = j.id
                WHERE j.id = ?
                """, expected.jobId());
        String hash = (String) row.get("tx_hash");
        String orgRef = Numeric.toHexString((byte[]) row.get("org_ref"));
        String caseRef = Numeric.toHexString((byte[]) row.get("case_ref"));
        String eventId = Numeric.toHexString((byte[]) row.get("logical_event_id"));
        byte[] commitment = (byte[]) row.get("evidence_commitment");
        int schemaVersion = ((Number) row.get("schema_version")).intValue();

        assertThat(row).containsEntry("status", "CONFIRMED").containsEntry("attempt_status", "CONFIRMED")
                .containsEntry("chain_id", LOCAL_CHAIN_ID).containsEntry("attempt_chain_id", LOCAL_CHAIN_ID)
                .containsEntry("event_type", expected.eventType()).containsEntry("chain_sequence", sequence)
                .containsEntry("contract_address", registry).containsEntry("signer_address", signer.toLowerCase(Locale.ROOT));
        assertThat(schemaVersion).isEqualTo(1);
        assertThat(((Number) row.get("receipt_status")).intValue()).isEqualTo(1);
        assertThat(count("blockchain_transactions", "blockchain_job_id", expected.jobId())).isEqualTo(1);

        assertThat(receipt.getTransactionHash()).isEqualTo(hash);
        assertThat(receipt.getStatus()).isEqualTo("0x1");
        assertThat(receipt.getTo()).isEqualToIgnoringCase(registry);
        assertThat(receipt.getFrom()).isEqualToIgnoringCase(signer);
        var block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(receipt.getBlockNumber()), false)
                .send().getBlock();
        assertThat(block.getHash()).isEqualTo(receipt.getBlockHash());
        assertThat(block.getNumber().longValueExact()).isEqualTo(((Number) row.get("block_number")).longValue());

        String extraTypes = ",bytes32".repeat(expected.refs().size());
        String functionSignature = expected.functionName() + "(bytes32,bytes32,bytes32,uint64,bytes32,uint16" + extraTypes + ")";
        String eventSignature = expected.logName() + "(bytes32,bytes32,bytes32,uint64,bytes32,uint16,uint64" + extraTypes + ")";
        String topic0 = Hash.sha3String(eventSignature);
        byte[] selector = Numeric.hexStringToByteArray(Hash.sha3String(functionSignature).substring(0, 10));
        String refs = String.join("", expected.refs().stream().map(Numeric::cleanHexPrefix).toList());
        String sequenceWord = TypeEncoder.encode(new Uint64(BigInteger.valueOf(sequence)));
        String commitmentWord = TypeEncoder.encode(new Bytes32(commitment));
        String schemaWord = TypeEncoder.encode(new Uint16(BigInteger.valueOf(schemaVersion)));
        String expectedInput = Numeric.toHexString(selector) + Numeric.cleanHexPrefix(orgRef)
                + Numeric.cleanHexPrefix(caseRef) + Numeric.cleanHexPrefix(eventId)
                + sequenceWord + commitmentWord + schemaWord + refs;
        var transaction = web3j.ethGetTransactionByHash(hash).send().getTransaction().orElseThrow();
        assertThat(transaction.getInput()).isEqualTo(expectedInput);
        assertThat(transaction.getNonce().longValueExact()).isEqualTo(((Number) row.get("nonce")).longValue());

        var logs = receipt.getLogs().stream().filter(log -> registry.equalsIgnoreCase(log.getAddress()))
                .filter(log -> !log.getTopics().isEmpty() && topic0.equals(log.getTopics().getFirst())).toList();
        assertThat(logs).hasSize(1);
        var log = logs.getFirst();
        assertThat(log.isRemoved()).isFalse();
        assertThat(log.getTransactionHash()).isEqualTo(hash);
        assertThat(log.getBlockHash()).isEqualTo(block.getHash());
        assertThat(log.getTopics()).containsExactly(topic0, orgRef, caseRef, eventId);
        assertThat(log.getData()).isEqualTo("0x" + sequenceWord + commitmentWord + schemaWord
                + TypeEncoder.encode(new Uint64(block.getTimestamp())) + refs);
        assertThat(log.getLogIndex()).isNotNull();

        var proof = json.readTree((String) row.get("receipt_private"));
        assertThat(proof.path("eventType").asText()).isEqualTo(expected.eventType());
        assertThat(proof.path("eventId").asText()).isEqualTo(eventId);
        assertThat(proof.path("orgRef").asText()).isEqualTo(orgRef);
        assertThat(proof.path("caseRef").asText()).isEqualTo(caseRef);
        assertThat(proof.path("contractAddress").asText()).isEqualTo(registry);
        assertThat(proof.path("transactionHash").asText()).isEqualTo(hash);
        assertThat(proof.path("blockHash").asText()).isEqualTo(block.getHash());
        assertThat(proof.path("blockNumber").asLong()).isEqualTo(block.getNumber().longValueExact());
        assertThat(proof.path("logIndex").asLong()).isEqualTo(log.getLogIndex().longValueExact());
        assertThat(proof.path("chainSequence").asLong()).isEqualTo(sequence);
        assertThat(proof.path("schemaVersion").asInt()).isEqualTo(schemaVersion);

        List<Type> record = call(web3j, registry, new Function("getEventRecord",
                Arrays.<Type>asList(new Bytes32(Numeric.hexStringToByteArray(eventId))),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() { }, new TypeReference<Bytes4>() { },
                        new TypeReference<Bytes32>() { }, new TypeReference<Bytes32>() { }, new TypeReference<Uint64>() { },
                        new TypeReference<Bytes32>() { }, new TypeReference<Uint16>() { }, new TypeReference<Bytes32>() { })));
        assertThat(((Bool) record.get(0)).getValue()).isTrue();
        assertThat(((Bytes4) record.get(1)).getValue()).isEqualTo(selector);
        assertThat(Numeric.toHexString(((Bytes32) record.get(2)).getValue())).isEqualTo(orgRef);
        assertThat(Numeric.toHexString(((Bytes32) record.get(3)).getValue())).isEqualTo(caseRef);
        assertThat(((Uint64) record.get(4)).getValue().longValueExact()).isEqualTo(sequence);
        assertThat(((Bytes32) record.get(5)).getValue()).isEqualTo(commitment);
        assertThat(((Uint16) record.get(6)).getValue().intValueExact()).isEqualTo(schemaVersion);
        String digestInput = TypeEncoder.encode(new Bytes4(selector)) + Numeric.cleanHexPrefix(orgRef)
                + Numeric.cleanHexPrefix(caseRef) + sequenceWord + commitmentWord + schemaWord + refs;
        assertThat(((Bytes32) record.get(7)).getValue()).isEqualTo(Hash.sha3(Numeric.hexStringToByteArray(digestInput)));
        System.out.printf("C06_E2E sequence=%d operation=%s job=CONFIRMED attempt=CONFIRMED eventId=%s registry=%s txHash=%s block=%s logIndex=%s%n",
                sequence, expected.eventType(), eventId, registry, hash, block.getNumber(), log.getLogIndex());
    }

    private String deployC06(Web3j web3j, Credentials admin) throws Exception {
        byte[] raw = Files.readAllBytes(C06_ARTIFACT);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)))
                .as("C06 artifact SHA-256").isEqualTo(C06_ARTIFACT_SHA256);
        var artifact = json.readTree(raw);
        assertThat(artifact.path("buildInfoId").asText()).isEqualTo(C06_BUILD_ID);
        String creation = artifact.path("bytecode").asText() + TypeEncoder.encode(new Address(admin.getAddress()));
        var receipt = send(web3j, admin, null, creation, BigInteger.valueOf(5_000_000));
        String registry = receipt.getContractAddress().toLowerCase(Locale.ROOT);
        String code = web3j.ethGetCode(registry, DefaultBlockParameterName.LATEST).send().getCode();
        assertThat(Hash.sha3(code)).as("C06 deployed runtime Keccak-256").isEqualTo(C06_RUNTIME_HASH);
        return registry;
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
