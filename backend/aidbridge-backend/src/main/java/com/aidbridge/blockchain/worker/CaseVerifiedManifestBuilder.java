package com.aidbridge.blockchain.worker;

import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.envelope.AidRegistryAbi.Operation;
import com.aidbridge.blockchain.envelope.CanonicalJson;
import com.aidbridge.blockchain.envelope.EvidenceCommitments;
import java.text.Normalizer;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds the private CASE_VERIFIED evidence manifest from the reviewed case revision (D43) in the exact
 * schema of docs/fixtures/c01/golden-vector.input.json. Only allowlisted fields are copied: no names,
 * notes, aliases, titles or consent text. Duplicate IDs, non-integer quantities and malformed digests are
 * rejected before hashing.
 */
public class CaseVerifiedManifestBuilder {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final ObjectMapper json;

    public CaseVerifiedManifestBuilder(ObjectMapper json) {
        this.json = json;
    }

    public record Manifest(String canonical, byte[] canonicalUtf8) {
    }

    public Manifest build(CaseVerifiedSource source, byte[] eventId, long schemaVersion, byte[] actorRef) {
        JsonNode payload = json.readTree(source.getPayloadPrivate());
        if (!source.getVerificationId().toString().equals(payload.path("verificationId").asText())) {
            throw new IllegalStateException("MANIFEST_SOURCE_MISMATCH: verificationId differs from event_key");
        }
        if (source.getReviewedSnapshot() == null) {
            throw new IllegalStateException("MANIFEST_SOURCE_MISSING: reviewed case revision not found");
        }
        JsonNode snapshot = json.readTree(source.getReviewedSnapshot());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("actorRef", Numeric.toHexString(requireBytes32(actorRef, "actorRef")));
        manifest.put("caseRef", Numeric.toHexString(requireBytes32(source.getCasePublicRef(), "caseRef")));
        manifest.put("eventId", Numeric.toHexString(requireBytes32(eventId, "eventId")));
        manifest.put("eventType", "CASE_VERIFIED");
        manifest.put("evidenceVersions", evidenceVersions(snapshot.path("evidenceVersions")));
        manifest.put("goodsLines", goodsLines(snapshot.path("needs")));
        manifest.put("occurredAt", source.getOccurredAtUtc());
        manifest.put("orgRef", Numeric.toHexString(requireBytes32(source.getOrgPublicRef(), "orgRef")));
        manifest.put("reviewedCaseVersion", requireNonNegative(payload.path("reviewedCaseVersion"), "reviewedCaseVersion"));
        manifest.put("schemaVersion", schemaVersion);
        manifest.put("verificationId", source.getVerificationId().toString());

        String canonical = CanonicalJson.toCanonicalString(manifest);
        return new Manifest(canonical, CanonicalJson.toCanonicalUtf8(manifest));
    }

    Manifest buildGoodsMilestone(GoodsMilestoneSource source, BlockchainJob job, String registryAddress,
            Operation operation) {
        return switch (source) {
            case CommitmentMilestoneSource contribution -> buildCommitmentMilestone(contribution, job, registryAddress, operation);
            case DeliveryMilestoneSource delivery -> buildDelivery(delivery, job, registryAddress);
            default -> throw new IllegalStateException("UNKNOWN_GOODS_SOURCE");
        };
    }

    /** Local C06 profile for pledges, reductions, and receipts. Quantities come from the immutable audit snapshot. */
    private Manifest buildCommitmentMilestone(CommitmentMilestoneSource source, BlockchainJob job, String registryAddress,
            Operation operation) {
        JsonNode payload = json.readTree(source.getPayloadPrivate());
        if (requirePositive(payload.path("payloadVersion"), "payloadVersion") != 1
                || job.getSchemaVersion() != 1) {
            throw new IllegalStateException("COMMITMENT_PAYLOAD_VERSION_UNSUPPORTED");
        }
        if (!source.getContributionId().toString().equals(payload.path("contributionId").asText())) {
            throw new IllegalStateException("COMMITMENT_SOURCE_MISMATCH");
        }
        if (source.getPledgedLines() == null) {
            throw new IllegalStateException("PLEDGE_LINES_MISMATCH");
        }
        JsonNode originalLines = json.readTree(source.getPledgedLines());
        boolean pledge = operation == Operation.AID_PLEDGED;
        var lines = pledge ? campaignItemLines(payload.path("lines"), "PLEDGE") : contributionLines(payload.path("lines"), originalLines,
                operation == Operation.GOODS_RECEIVED ? "RECEIPT" : "REDUCTION");
        if (pledge && !lines.equals(campaignItemLines(originalLines, "PLEDGE"))) {
            throw new IllegalStateException("PLEDGE_LINES_MISMATCH");
        }

        String commitmentRef = publicRef(source.getCommitmentRef(), "commitmentRef");
        Map<String, Object> manifest = goodsContext(source, job, registryAddress, operation);
        manifest.put("contributionId", source.getContributionId().toString());
        manifest.put("commitmentRef", commitmentRef);
        manifest.put("lines", lines);
        if (operation == Operation.COMMITMENT_REDUCED) {
            JsonNode reason = payload.path("reason");
            if (!reason.isString() || reason.asText().isBlank() || reason.asText().length() > 500) {
                throw new IllegalStateException("REDUCTION_REASON_INVALID");
            }
            manifest.put("reason", Normalizer.normalize(reason.asText(), Normalizer.Form.NFC));
        }
        if (operation == Operation.GOODS_RECEIVED) {
            if (source.getReceiptId() == null || !source.getReceiptId().toString().equals(payload.path("receiptId").asText())
                    || !commitmentRef.equals(payload.path("commitmentRef").asText())) {
                throw new IllegalStateException("RECEIPT_SOURCE_MISMATCH");
            }
            if (source.getReceivedLines() == null || !lines.equals(
                    contributionLines(json.readTree(source.getReceivedLines()), originalLines, "RECEIPT"))) {
                throw new IllegalStateException("RECEIPT_LINES_MISMATCH");
            }
            manifest.put("receiptId", source.getReceiptId().toString());
            manifest.put("receiptRef", receiptRef(payload));
            manifest.put("receivedAt", snapshotTime(payload.path("receivedAt").asText(), source.getReceivedAtUtc(), "RECEIPT"));
        }
        return new Manifest(CanonicalJson.toCanonicalString(manifest), CanonicalJson.toCanonicalUtf8(manifest));
    }

    private Manifest buildDelivery(DeliveryMilestoneSource source, BlockchainJob job, String registryAddress) {
        JsonNode payload = json.readTree(source.getPayloadPrivate());
        if (requirePositive(payload.path("payloadVersion"), "payloadVersion") != 1 || job.getSchemaVersion() != 1) {
            throw new IllegalStateException("DELIVERY_PAYLOAD_VERSION_UNSUPPORTED");
        }
        String deliveryRef = publicRef(source.getDeliveryRef(), "deliveryRef");
        if (!source.getDeliveryId().toString().equals(payload.path("deliveryId").asText())
                || !source.getCampaignId().toString().equals(payload.path("campaignId").asText())
                || !deliveryRef.equals(payload.path("deliveryRef").asText())) {
            throw new IllegalStateException("DELIVERY_SOURCE_MISMATCH");
        }
        var lines = campaignItemLines(payload.path("lines"), "DELIVERY");
        if (source.getDeliveredLines() == null
                || !lines.equals(campaignItemLines(json.readTree(source.getDeliveredLines()), "DELIVERY"))) {
            throw new IllegalStateException("DELIVERY_LINES_MISMATCH");
        }
        if (source.getEvidenceVersions() == null || !payload.path("evidenceVersionIds").isArray()) {
            throw new IllegalStateException("DELIVERY_EVIDENCE_MISSING");
        }
        var versions = evidenceVersions(json.readTree(source.getEvidenceVersions()));
        List<String> evidenceIds = new ArrayList<>();
        for (JsonNode id : payload.path("evidenceVersionIds")) {
            evidenceIds.add(UUID.fromString(id.asText()).toString());
        }
        evidenceIds.sort(String::compareTo);
        if (!evidenceIds.equals(versions.stream().map(v -> (String) v.get("evidenceVersionId")).toList())) {
            throw new IllegalStateException("DELIVERY_EVIDENCE_MISMATCH");
        }

        Map<String, Object> manifest = goodsContext(source, job, registryAddress, Operation.DELIVERY_RECORDED);
        manifest.put("deliveryId", source.getDeliveryId().toString());
        manifest.put("campaignId", source.getCampaignId().toString());
        manifest.put("deliveryRef", deliveryRef);
        manifest.put("lines", lines);
        manifest.put("evidenceVersions", versions);
        JsonNode handedOverAt = payload.path("handedOverAt");
        if (source.getHandedOverAtUtc() != null || (!handedOverAt.isNull() && !handedOverAt.isMissingNode())) {
            manifest.put("handedOverAt", snapshotTime(handedOverAt.asText(), source.getHandedOverAtUtc(), "DELIVERY"));
        }
        return new Manifest(CanonicalJson.toCanonicalString(manifest), CanonicalJson.toCanonicalUtf8(manifest));
    }

    private static Map<String, Object> goodsContext(GoodsMilestoneSource source, BlockchainJob job,
            String registryAddress, Operation operation) {
        return new LinkedHashMap<>(Map.ofEntries(
                Map.entry("orgRef", publicRef(source.getOrgPublicRef(), "orgRef")),
                Map.entry("caseRef", publicRef(source.getCasePublicRef(), "caseRef")),
                Map.entry("eventId", publicRef(job.getLogicalEventId(), "eventId")),
                Map.entry("eventType", operation.name()),
                Map.entry("chainSequence", job.getChainSequence()),
                Map.entry("schemaVersion", job.getSchemaVersion()),
                Map.entry("chainId", job.getChainId()),
                Map.entry("registryAddress", registryAddress.toLowerCase(Locale.ROOT)),
                Map.entry("payloadVersion", 1),
                Map.entry("occurredAt", source.getOccurredAtUtc())));
    }

    List<Bytes32> preparedGoodsRefs(GoodsMilestoneSource source, BlockchainJob job, String registryAddress,
            Operation operation) {
        JsonNode manifest = json.readTree(job.getManifestPrivate());
        if (!registryAddress.equalsIgnoreCase(manifest.path("registryAddress").asText())
                || job.getChainId() != requirePositive(manifest.path("chainId"), "chainId")) {
            throw new IllegalStateException("GOODS_PREPARATION_DEPLOYMENT_MISMATCH");
        }
        if (!operation.name().equals(manifest.path("eventType").asText())
                || job.getChainSequence() != requirePositive(manifest.path("chainSequence"), "chainSequence")
                || job.getSchemaVersion() != requirePositive(manifest.path("schemaVersion"), "schemaVersion")
                || !publicRef(source.getOrgPublicRef(), "orgRef").equals(manifest.path("orgRef").asText())
                || !publicRef(source.getCasePublicRef(), "caseRef").equals(manifest.path("caseRef").asText())
                || !publicRef(job.getLogicalEventId(), "eventId").equals(manifest.path("eventId").asText())) {
            throw new IllegalStateException("GOODS_PREPARATION_MISMATCH");
        }
        return switch (source) {
            case CommitmentMilestoneSource contribution -> preparedCommitmentRefs(contribution, manifest, operation);
            case DeliveryMilestoneSource delivery -> preparedDeliveryRefs(delivery, manifest);
            default -> throw new IllegalStateException("UNKNOWN_GOODS_SOURCE");
        };
    }

    private List<Bytes32> preparedCommitmentRefs(CommitmentMilestoneSource source, JsonNode manifest,
            Operation operation) {
        String commitmentRef = manifest.path("commitmentRef").asText();
        if (!source.getContributionId().toString().equals(manifest.path("contributionId").asText())
                || !publicRef(source.getCommitmentRef(), "commitmentRef").equals(commitmentRef)) {
            throw new IllegalStateException("COMMITMENT_PREPARATION_MISMATCH");
        }
        Bytes32 pledgeRef = new Bytes32(Numeric.hexStringToByteArray(commitmentRef));
        if (operation == Operation.GOODS_RECEIVED) {
            String storedReceiptRef = manifest.path("receiptRef").asText();
            if (source.getReceiptId() == null || !source.getReceiptId().toString().equals(manifest.path("receiptId").asText())
                    || !receiptRef(json.readTree(source.getPayloadPrivate())).equals(storedReceiptRef)) {
                throw new IllegalStateException("RECEIPT_PREPARATION_MISMATCH");
            }
            return List.of(new Bytes32(Numeric.hexStringToByteArray(storedReceiptRef)), pledgeRef);
        }
        return List.of(pledgeRef);
    }

    private List<Bytes32> preparedDeliveryRefs(DeliveryMilestoneSource source, JsonNode manifest) {
        String deliveryRef = manifest.path("deliveryRef").asText();
        JsonNode payload = json.readTree(source.getPayloadPrivate());
        if (!source.getDeliveryId().toString().equals(manifest.path("deliveryId").asText())
                || !source.getCampaignId().toString().equals(manifest.path("campaignId").asText())
                || !publicRef(source.getDeliveryRef(), "deliveryRef").equals(deliveryRef)
                || !deliveryRef.equals(payload.path("deliveryRef").asText())) {
            throw new IllegalStateException("DELIVERY_PREPARATION_MISMATCH");
        }
        return List.of(new Bytes32(Numeric.hexStringToByteArray(deliveryRef)));
    }

    private static String receiptRef(JsonNode payload) {
        String ref = payload.path("receiptRef").asText();
        if (!ref.matches("0x[0-9a-f]{64}")) {
            throw new IllegalStateException("RECEIPT_REF_INVALID");
        }
        return publicRef(Numeric.hexStringToByteArray(ref), "receiptRef");
    }

    private static String snapshotTime(String snapshotTime, String storedTime, String kind) {
        try {
            Instant at = Instant.parse(snapshotTime);
            if (storedTime == null || !at.equals(Instant.parse(storedTime))) {
                throw new IllegalStateException(kind + "_TIME_MISMATCH");
            }
            return at.toString();
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(kind + "_TIME_INVALID");
        }
    }

    private static List<Map<String, Object>> campaignItemLines(JsonNode array, String kind) {
        if (!array.isArray() || array.size() == 0) {
            throw new IllegalStateException(kind + "_LINES_MISSING");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode line : array) {
            String itemId = UUID.fromString(line.path("campaignItemId").asText()).toString();
            if (!ids.add(itemId)) {
                throw new IllegalStateException(kind + "_DUPLICATE_LINE");
            }
            long quantity = requirePositive(line.path("quantity"), "quantity");
            rows.add(Map.of("campaignItemId", itemId, "quantity", quantity));
        }
        rows.sort(Comparator.comparing(row -> (String) row.get("campaignItemId")));
        return rows;
    }

    private static List<Map<String, Object>> contributionLines(JsonNode lines, JsonNode originalLines, String kind) {
        if (!lines.isArray() || lines.size() == 0) {
            throw new IllegalStateException(kind + "_LINES_MISSING");
        }
        Map<String, JsonNode> pledgedById = new LinkedHashMap<>();
        for (JsonNode line : originalLines) {
            pledgedById.put(line.path("contributionItemId").asText(), line);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode line : lines) {
            String contributionItemId = UUID.fromString(line.path("contributionItemId").asText()).toString();
            String campaignItemId = UUID.fromString(line.path("campaignItemId").asText()).toString();
            if (!ids.add(contributionItemId)) {
                throw new IllegalStateException(kind + "_DUPLICATE_LINE");
            }
            long quantity = requirePositive(line.path("quantity"), "quantity");
            JsonNode original = pledgedById.get(contributionItemId);
            if (original == null || !campaignItemId.equals(original.path("campaignItemId").asText())
                    || quantity > requirePositive(original.path("quantity"), "quantity")) {
                throw new IllegalStateException(kind + "_LINE_MISMATCH");
            }
            rows.add(Map.of("contributionItemId", contributionItemId, "campaignItemId", campaignItemId,
                    "quantity", quantity));
        }
        rows.sort(Comparator.comparing(row -> (String) row.get("contributionItemId")));
        return rows;
    }

    private static String publicRef(byte[] value, String name) {
        return Numeric.toHexString(EvidenceCommitments.requireNonZeroBytes32(value, name));
    }

    private static List<Map<String, Object>> evidenceVersions(JsonNode array) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);
            String id = UUID.fromString(node.path("id").asText()).toString();
            String sha256 = node.path("sha256").asText().toLowerCase();
            if (!SHA256_HEX.matcher(sha256).matches()) {
                throw new IllegalStateException("MANIFEST_INVALID_EVIDENCE_DIGEST");
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("MANIFEST_DUPLICATE_EVIDENCE_VERSION");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("evidenceVersionId", id);
            row.put("sha256", sha256);
            row.put("versionNo", requirePositive(node.path("versionNo"), "versionNo"));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("MANIFEST_SOURCE_MISSING: reviewed revision has no evidence versions");
        }
        rows.sort(Comparator.comparing(row -> (String) row.get("evidenceVersionId")));
        return rows;
    }

    private static List<Map<String, Object>> goodsLines(JsonNode array) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);
            String id = UUID.fromString(node.path("id").asText()).toString();
            if (!ids.add(id)) {
                throw new IllegalStateException("MANIFEST_DUPLICATE_GOODS_LINE");
            }
            String unit = Normalizer.normalize(node.path("unit").asText(), Normalizer.Form.NFC);
            if (unit.isBlank()) {
                throw new IllegalStateException("MANIFEST_INVALID_UNIT");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("itemId", id);
            row.put("quantity", requirePositive(node.path("quantity"), "quantity"));
            row.put("unit", unit);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("MANIFEST_SOURCE_MISSING: reviewed revision has no goods lines");
        }
        rows.sort(Comparator.comparing(row -> (String) row.get("itemId")));
        return rows;
    }

    private static long requirePositive(JsonNode node, String name) {
        long value = requireNonNegative(node, name);
        if (value == 0) {
            throw new IllegalStateException("MANIFEST_INVALID_" + name.toUpperCase() + ": must be positive");
        }
        return value;
    }

    private static long requireNonNegative(JsonNode node, String name) {
        if (node == null || !node.isIntegralNumber() || node.asLong() < 0) {
            throw new IllegalStateException("MANIFEST_INVALID_" + name.toUpperCase() + ": must be a non-negative integer");
        }
        return node.asLong();
    }

    private static byte[] requireBytes32(byte[] value, String name) {
        if (value == null || value.length != 32) {
            throw new IllegalStateException("MANIFEST_INVALID_" + name.toUpperCase() + ": must be 32 bytes");
        }
        return value;
    }
}
