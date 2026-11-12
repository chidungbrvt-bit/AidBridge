package com.aidbridge.blockchain.worker;

import com.aidbridge.blockchain.envelope.CanonicalJson;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
