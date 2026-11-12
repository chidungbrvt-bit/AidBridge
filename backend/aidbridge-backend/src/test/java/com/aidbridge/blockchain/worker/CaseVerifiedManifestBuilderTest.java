package com.aidbridge.blockchain.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The manifest built from a real-shaped reviewed snapshot (CaseDetailResponse JSON, including private
 * fields) must equal the golden-vector canonical manifest byte for byte and carry none of the private fields.
 */
class CaseVerifiedManifestBuilderTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final CaseVerifiedManifestBuilder builder = new CaseVerifiedManifestBuilder(JSON);

    // Same values as docs/fixtures/c01/golden-vector.input.json, plus private fields that must be dropped.
    private static final String SNAPSHOT = """
            {"id":"99999999-9999-4999-8999-999999999999","beneficiaryAlias":"PRIVATE-ALIAS-MARKER",
             "internalTitle":"PRIVATE-TITLE-MARKER","approximateArea":"PRIVATE-AREA-MARKER",
             "needs":[
               {"id":"33333333-3333-4333-8333-333333333332","name":"PRIVATE-NAME-WATER","unit":"box","quantity":4},
               {"id":"33333333-3333-4333-8333-333333333331","name":"PRIVATE-NAME-RICE","unit":"kg","quantity":20}],
             "evidenceVersions":[
               {"id":"55555555-5555-4555-8555-555555555552","evidenceFileId":"66666666-6666-4666-8666-666666666662",
                "versionNo":1,"mimeType":"text/plain","byteSize":10,
                "sha256":"2222222222222222222222222222222222222222222222222222222222222222","createdAt":"2026-11-09T00:00:00Z"},
               {"id":"55555555-5555-4555-8555-555555555551","evidenceFileId":"66666666-6666-4666-8666-666666666661",
                "versionNo":1,"mimeType":"text/plain","byteSize":10,
                "sha256":"1111111111111111111111111111111111111111111111111111111111111111","createdAt":"2026-11-09T00:00:00Z"}]}
            """;

    private record Source(String payloadPrivate, String reviewedSnapshot) implements CaseVerifiedSource {
        public UUID getJobId() { return UUID.randomUUID(); }
        public UUID getVerificationId() { return UUID.fromString("77777777-7777-4777-8777-777777777777"); }
        public String getOccurredAtUtc() { return "2026-11-09T01:00:00.000Z"; }
        public String getPayloadPrivate() { return payloadPrivate; }
        public UUID getOrganizationId() { return UUID.randomUUID(); }
        public String getOrganizationStatus() { return "APPROVED"; }
        public byte[] getOrgPublicRef() { return Numeric.hexStringToByteArray("0x" + "10".repeat(32)); }
        public String getRelayerAddress() { return null; }
        public UUID getCaseId() { return UUID.randomUUID(); }
        public byte[] getCasePublicRef() { return Numeric.hexStringToByteArray("0x" + "20".repeat(32)); }
        public String getReviewedSnapshot() { return reviewedSnapshot; }
    }

    private static final String PAYLOAD =
            "{\"payloadVersion\":1,\"verificationId\":\"77777777-7777-4777-8777-777777777777\",\"reviewedCaseVersion\":0}";

    private CaseVerifiedManifestBuilder.Manifest build(String payload, String snapshot) {
        return builder.build(new Source(payload, snapshot), Numeric.hexStringToByteArray("0x" + "30".repeat(32)), 1,
                Numeric.hexStringToByteArray("0x" + "50".repeat(32)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsTheGoldenVectorManifestAndDropsPrivateFields() throws Exception {
        Map<String, Object> vector = JSON.readValue(
                Files.readString(Path.of("../../docs/fixtures/c01/golden-vector.input.json")), Map.class);

        CaseVerifiedManifestBuilder.Manifest manifest = build(PAYLOAD, SNAPSHOT);

        assertThat(manifest.canonical()).isEqualTo(vector.get("canonicalManifestUtf8"));
        assertThat(manifest.canonical()).doesNotContain("PRIVATE-");
    }

    @Test
    void rejectsMismatchedVerificationMissingRevisionDuplicatesAndBadValues() {
        assertThatThrownBy(() -> build(PAYLOAD.replace("77777777-7777-4777-8777-777777777777",
                "88888888-8888-4888-8888-888888888888"), SNAPSHOT)).hasMessageStartingWith("MANIFEST_SOURCE_MISMATCH");
        assertThatThrownBy(() -> build(PAYLOAD, null)).hasMessageStartingWith("MANIFEST_SOURCE_MISSING");
        assertThatThrownBy(() -> build(PAYLOAD, SNAPSHOT.replace("333333333332", "333333333331")))
                .hasMessage("MANIFEST_DUPLICATE_GOODS_LINE");
        assertThatThrownBy(() -> build(PAYLOAD, SNAPSHOT.replace("\"quantity\":4", "\"quantity\":4.5")))
                .hasMessageStartingWith("MANIFEST_INVALID_QUANTITY");
        assertThatThrownBy(() -> build(PAYLOAD, SNAPSHOT.replace("\"sha256\":\"2222", "\"sha256\":\"zz22")))
                .hasMessage("MANIFEST_INVALID_EVIDENCE_DIGEST");
    }
}
