package com.aidbridge.blockchain.envelope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

import tools.jackson.databind.json.JsonMapper;

/**
 * C01-T16 (IT14, BR08/09): Java must reproduce the TypeScript golden vector byte for byte.
 * Expected values are the candidates printed by {@code node docs/fixtures/c01/verify-c01.mjs --ethers}
 * and the payloadDigest stored on-chain by AidRegistry (C03 test C01-T01).
 */
class GoldenVectorParityTest {

    private static final Path FIXTURES = Path.of("../../docs/fixtures/c01");

    private static final String EXPECTED_DOMAIN_V1 =
            "0x4a5cac579b9b766d3889e71136e028adaa4b8d636ff24d257afcc5803ac49397";
    private static final String EXPECTED_COMMITMENT =
            "0x0a00e80664506e74cbc0fa19c4e17cf3fdace8c2d70e5717b08c6804dc5c8882";
    private static final String EXPECTED_PAYLOAD_DIGEST =
            "0xcff6ea47eb121a61875dceef9200e998d5096f69d9fb7e7435ee6b2b8f74b1f8";
    private static final String EXPECTED_SELECTOR = "0x1bd1fadf";
    private static final String EXPECTED_TOPIC0 =
            "0xc52bfb1c97b1de28337fe01b8bf6dce1267ee4b6f337c271935032cdf0629ea2";

    private static Map<String, Object> vector;
    private static Map<String, Object> reviewedSample;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixtures() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        vector = mapper.readValue(Files.readString(FIXTURES.resolve("golden-vector.input.json")), Map.class);
        reviewedSample = mapper.readValue(Files.readString(FIXTURES.resolve("case-verified.reviewed.json")), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private static byte[] hex(Object value) {
        return Numeric.hexStringToByteArray((String) value);
    }

    @Test
    void canonicalManifestMatchesTypeScriptBytesAndSha256() {
        String canonical = CanonicalJson.toCanonicalString(section(vector, "manifest"));

        assertThat(canonical).isEqualTo(vector.get("canonicalManifestUtf8"));
        assertThat(Numeric.toHexString(canonical.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(vector.get("canonicalManifestUtf8Hex"));
        assertThat(Numeric.toHexString(EvidenceCommitments.manifestDigest(CanonicalJson.toCanonicalUtf8(section(vector, "manifest")))))
                .isEqualTo(vector.get("manifestDigestSha256"));
    }

    @Test
    void domainSelectorAndTopicMatchFrozenD49Values() {
        assertThat(Numeric.toHexString(EvidenceCommitments.DOMAIN_V1)).isEqualTo(EXPECTED_DOMAIN_V1);
        assertThat(Numeric.toHexString(AidRegistryAbi.RECORD_CASE_VERIFICATION_SELECTOR)).isEqualTo(EXPECTED_SELECTOR);
        assertThat(AidRegistryAbi.CASE_VERIFIED_TOPIC0).isEqualTo(EXPECTED_TOPIC0);
    }

    @Test
    void evidenceCommitmentAndPayloadDigestMatchGoldenVector() {
        Map<String, Object> inputs = section(vector, "commitmentInputs");
        byte[] commitment = EvidenceCommitments.evidenceCommitment(
                ((Number) inputs.get("chainId")).longValue(),
                (String) inputs.get("registryAddress"),
                hex(inputs.get("caseRef")),
                hex(inputs.get("eventId")),
                hex(vector.get("manifestDigestSha256")),
                hex(inputs.get("salt")));
        assertThat(Numeric.toHexString(commitment)).isEqualTo(EXPECTED_COMMITMENT);

        Map<String, Object> args = section(section(reviewedSample, "publicEnvelope"), "arguments");
        CaseVerifiedEnvelope envelope = new CaseVerifiedEnvelope(
                hex(args.get("orgRef")), hex(args.get("caseRef")), hex(args.get("eventId")),
                ((Number) args.get("chainSequence")).longValue(), commitment,
                ((Number) args.get("schemaVersion")).intValue());
        assertThat(Numeric.toHexString(envelope.payloadDigest())).isEqualTo(EXPECTED_PAYLOAD_DIGEST);
    }

    @Test
    void calldataMatchesReviewedSampleEncoding() {
        Map<String, Object> args = section(section(reviewedSample, "publicEnvelope"), "arguments");
        CaseVerifiedEnvelope envelope = new CaseVerifiedEnvelope(
                hex(args.get("orgRef")), hex(args.get("caseRef")), hex(args.get("eventId")),
                ((Number) args.get("chainSequence")).longValue(), hex(args.get("evidenceCommitment")),
                ((Number) args.get("schemaVersion")).intValue());
        String encodedArguments = (String) section(reviewedSample, "encodingExpectation").get("encodedArguments");

        String calldata = AidRegistryAbi.encodeRecordCaseVerification(envelope);

        assertThat(calldata).isEqualTo(EXPECTED_SELECTOR + encodedArguments.substring(2));
        assertThat((calldata.length() - 2) / 2).isEqualTo(4 + 6 * 32);
    }

    @Test
    void canonicalizationIsKeyOrderIndependentAndChangesWithEvidence() {
        Map<String, Object> manifest = section(vector, "manifest");
        Map<String, Object> reversed = new LinkedHashMap<>();
        List<String> keys = new java.util.ArrayList<>(manifest.keySet());
        java.util.Collections.reverse(keys);
        keys.forEach(key -> reversed.put(key, manifest.get(key)));
        assertThat(CanonicalJson.toCanonicalString(reversed)).isEqualTo(vector.get("canonicalManifestUtf8"));

        Map<String, Object> changed = new LinkedHashMap<>(manifest);
        changed.put("reviewedCaseVersion", 1);
        assertThat(EvidenceCommitments.manifestDigest(CanonicalJson.toCanonicalUtf8(changed)))
                .isNotEqualTo(hex(vector.get("manifestDigestSha256")));
    }

    @Test
    void commitmentChangesWithSaltChainIdOrRegistry() {
        Map<String, Object> inputs = section(vector, "commitmentInputs");
        byte[] caseRef = hex(inputs.get("caseRef"));
        byte[] eventId = hex(inputs.get("eventId"));
        byte[] digest = hex(vector.get("manifestDigestSha256"));
        byte[] salt = hex(inputs.get("salt"));
        byte[] otherSalt = salt.clone();
        otherSalt[31] ^= 0x01;

        assertThat(EvidenceCommitments.evidenceCommitment(43113, "0x000000000000000000000000000000000000c0de", caseRef, eventId, digest, otherSalt))
                .isNotEqualTo(hex(EXPECTED_COMMITMENT));
        assertThat(EvidenceCommitments.evidenceCommitment(31337, "0x000000000000000000000000000000000000c0de", caseRef, eventId, digest, salt))
                .isNotEqualTo(hex(EXPECTED_COMMITMENT));
        assertThat(EvidenceCommitments.evidenceCommitment(43113, "0x000000000000000000000000000000000000c0df", caseRef, eventId, digest, salt))
                .isNotEqualTo(hex(EXPECTED_COMMITMENT));
    }

    @Test
    void canonicalizationRejectsNullsFloatsNegativesAndNonNfcStrings() {
        assertThatThrownBy(() -> CanonicalJson.toCanonicalString(java.util.Collections.singletonMap("a", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalJson.toCanonicalString(Map.of("quantity", 1.5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CanonicalJson.toCanonicalString(Map.of("quantity", -1)))
                .isInstanceOf(IllegalArgumentException.class);
        // "e" + combining acute accent (NFD) must be normalized by the manifest builder before hashing
        assertThatThrownBy(() -> CanonicalJson.toCanonicalString(Map.of("unit", "é")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CanonicalJson.toCanonicalString(Map.of("unit", "é"))).isEqualTo("{\"unit\":\"é\"}");
    }

    @Test
    void envelopeRejectsZeroRefsAndUnsupportedSchema() {
        byte[] ref = hex("0x" + "11".repeat(32));
        assertThatThrownBy(() -> new CaseVerifiedEnvelope(new byte[32], ref, ref, 1, ref, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseVerifiedEnvelope(ref, ref, ref, 0, ref, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseVerifiedEnvelope(ref, ref, ref, 1, ref, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
