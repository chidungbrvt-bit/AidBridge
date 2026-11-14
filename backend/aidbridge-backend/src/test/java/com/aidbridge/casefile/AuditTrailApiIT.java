package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * T01 GET /api/cases/{id}/audit-trail against PostgreSQL. Job/attempt rows are seeded with JDBC in the
 * isolated test schema to cover every chain state; the API itself is read-only.
 */
class AuditTrailApiIT extends AbstractCampaignHttpIT {
    static final String CONTRACT = "0x015D941c679b0c402c8262BE237adAD36cEf6726";
    static final String PAYLOAD_MARKER = "PRIVATE_PAYLOAD_MARKER";
    static final String MANIFEST_MARKER = "PRIVATE_MANIFEST_MARKER";
    static final String SNOWTRACE = "https://testnet.snowtrace.io/tx/";

    @Test
    void everyChainStateIsReportedHonestly() throws Exception {
        UUID caseId = verifiedCase();
        Instant base = Instant.now().plus(1, ChronoUnit.HOURS);

        UUID pending = event(caseId, "AID_PLEDGED", base.plusSeconds(1));
        job(pending, caseId, "PENDING", null, 101);

        UUID processing = event(caseId, "AID_PLEDGED", base.plusSeconds(2));
        String preparedHash = hash();
        tx(job(processing, caseId, "PROCESSING", null, 102), preparedHash, "PREPARED", null, 43113);

        UUID submitted = event(caseId, "GOODS_RECEIVED", base.plusSeconds(3));
        String submittedHash = hash();
        tx(job(submitted, caseId, "SUBMITTED", "RECEIPT_PENDING_OPERATOR_ATTENTION", 103),
                submittedHash, "SUBMITTED", null, 43113);

        UUID confirmed = event(caseId, "GOODS_RECEIVED", base.plusSeconds(4));
        String confirmedHash = hash();
        tx(job(confirmed, caseId, "CONFIRMED", null, 104), confirmedHash, "CONFIRMED", 1234L, 43113);

        UUID reverted = event(caseId, "DELIVERY_RECORDED", base.plusSeconds(5));
        String revertedHash = hash();
        tx(job(reverted, caseId, "FAILED", "TX_REVERTED", 105), revertedHash, "REVERTED", 1235L, 43113);

        UUID mismatch = event(caseId, "DELIVERY_RECORDED", base.plusSeconds(6));
        String mismatchHash = hash();
        tx(job(mismatch, caseId, "FAILED", "RECEIPT_WRONG_CONTRACT", 106), mismatchHash, "SUBMITTED", null, 43113);

        UUID local = event(caseId, "AID_PLEDGED", base.plusSeconds(7));
        String localHash = hash();
        tx(job(local, caseId, "CONFIRMED", null, 107), localHash, "CONFIRMED", 7L, 31337);

        UUID confirmedWithoutReceipt = event(caseId, "AID_PLEDGED", base.plusSeconds(8));
        tx(job(confirmedWithoutReceipt, caseId, "CONFIRMED", null, 108), hash(), "SUBMITTED", null, 43113);

        UUID noJob = event(caseId, "CASE_REJECTED", base.plusSeconds(9));

        var response = volunteer.browser().send("GET", "/api/cases/" + caseId + "/audit-trail?size=100", null, null, false);
        status(response, 200);
        JsonNode body = node(response);

        JsonNode p = item(body, pending).path("blockchain");
        assertThat(p.path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertAbsent(p, "txHash", "blockNumber", "explorerUrl", "errorCode");
        assertThat(item(body, pending).path("logicalEventId").asText()).matches("^0x[0-9a-f]{64}$");

        JsonNode pr = item(body, processing).path("blockchain");
        assertThat(pr.path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertAbsent(pr, "txHash", "explorerUrl");

        JsonNode s = item(body, submitted).path("blockchain");
        assertThat(s.path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(s.path("txHash").asText()).isEqualTo(submittedHash);
        assertThat(s.path("explorerUrl").asText()).isEqualTo(SNOWTRACE + submittedHash);
        assertAbsent(s, "blockNumber", "errorCode");

        JsonNode c = item(body, confirmed).path("blockchain");
        assertThat(c.path("status").asText()).isEqualTo("CONFIRMED_ON_CHAIN");
        assertThat(c.path("chainId").asLong()).isEqualTo(43113L);
        assertThat(c.path("network").asText()).isEqualTo("Avalanche Fuji testnet");
        assertThat(c.path("contractAddress").asText()).isEqualTo(CONTRACT);
        assertThat(c.path("txHash").asText()).isEqualTo(confirmedHash);
        assertThat(c.path("blockNumber").asLong()).isEqualTo(1234L);
        assertThat(c.path("explorerUrl").asText()).isEqualTo(SNOWTRACE + confirmedHash);

        JsonNode r = item(body, reverted).path("blockchain");
        assertThat(r.path("status").asText()).isEqualTo("FAILED_ON_CHAIN");
        assertThat(r.path("txHash").asText()).isEqualTo(revertedHash);
        assertThat(r.path("blockNumber").asLong()).isEqualTo(1235L);
        assertThat(r.path("errorCode").asText()).isEqualTo("TX_REVERTED");

        JsonNode m = item(body, mismatch).path("blockchain");
        assertThat(m.path("status").asText()).isEqualTo("FAILED_ON_CHAIN");
        assertThat(m.path("txHash").asText()).isEqualTo(mismatchHash);
        assertThat(m.path("errorCode").asText()).isEqualTo("RECEIPT_WRONG_CONTRACT");

        JsonNode l = item(body, local).path("blockchain");
        assertThat(l.path("txHash").asText()).isEqualTo(localHash);
        assertAbsent(l, "explorerUrl");

        assertThat(item(body, confirmedWithoutReceipt).path("blockchain").path("status").asText())
                .isEqualTo("PENDING_ON_CHAIN");

        JsonNode n = item(body, noJob);
        assertThat(n.path("blockchain").path("status").asText()).isEqualTo("NOT_APPLICABLE");
        assertAbsent(n, "logicalEventId");
        assertThat(n.path("actor").path("id").asText()).isEqualTo(verifier.id().toString());

        // Real events written by B04/B05 are part of the trail too.
        assertThat(types(body)).contains("CASE_CREATED", "CASE_VERIFIED");
        JsonNode verified = byType(body, "CASE_VERIFIED");
        assertThat(verified.path("blockchain").path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(verified.path("actor").path("id").asText()).isEqualTo(verifier.id().toString());
        assertThat(byType(body, "CASE_CREATED").path("blockchain").path("status").asText()).isEqualTo("NOT_APPLICABLE");

        // Private material never leaves the database, and an unbroadcast hash is never shown.
        assertThat(response.body()).doesNotContain(PAYLOAD_MARKER, MANIFEST_MARKER, preparedHash,
                "payloadPrivate", "manifest", "salt", "signedTx", "receiptPrivate", "signer", "nonce");
    }

    @Test
    void onlyMembersOfTheOwningOrganizationCanRead() throws Exception {
        UUID caseId = verifiedCase();
        String path = "/api/cases/" + caseId + "/audit-trail";

        status(verifier.browser().send("GET", path, null, null, false), 200);
        problem(other.browser().send("GET", path, null, null, false), 404, "RESOURCE_NOT_FOUND");
        problem(admin.browser().send("GET", path, null, null, false), 404, "RESOURCE_NOT_FOUND");
        problem(volunteer.browser().send("GET", "/api/cases/" + UUID.randomUUID() + "/audit-trail", null, null, false),
                404, "RESOURCE_NOT_FOUND");

        var anonymous = new Browser().send("GET", path, null, null, false);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).doesNotContain("CASE_CREATED");
    }

    @Test
    void pagesAreStableAndBounded() throws Exception {
        UUID caseId = verifiedCase();
        Instant base = Instant.now().plus(2, ChronoUnit.HOURS);
        for (int i = 0; i < 4; i++) {
            event(caseId, "CASE_UPDATED", base); // identical timestamps: the id breaks the tie
        }
        List<String> expected = jdbc.queryForList(
                "SELECT id::text FROM audit_events WHERE aid_case_id = ? ORDER BY occurred_at, id", String.class, caseId);

        List<String> seen = new ArrayList<>();
        for (int page = 0; ; page++) {
            var response = volunteer.browser().send("GET",
                    "/api/cases/" + caseId + "/audit-trail?page=" + page + "&size=2", null, null, false);
            status(response, 200);
            JsonNode body = node(response);
            assertThat(body.path("totalElements").asLong()).isEqualTo(expected.size());
            assertThat(body.path("size").asInt()).isEqualTo(2);
            if (body.path("items").isEmpty()) break;
            assertThat(body.path("items").size()).isLessThanOrEqualTo(2);
            body.path("items").forEach(item -> seen.add(item.path("eventId").asText()));
        }
        assertThat(seen).containsExactlyElementsOf(expected);

        String path = "/api/cases/" + caseId + "/audit-trail";
        problem(volunteer.browser().send("GET", path + "?size=101", null, null, false), 400, "VALIDATION_ERROR");
        problem(volunteer.browser().send("GET", path + "?size=0", null, null, false), 400, "VALIDATION_ERROR");
        problem(volunteer.browser().send("GET", path + "?page=-1", null, null, false), 400, "VALIDATION_ERROR");
    }

    // ---- fixtures (test schema only) ---------------------------------------------------------------------

    UUID event(UUID caseId, String type, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_events (id, organization_id, aid_case_id, actor_user_id, entity_type, entity_id,
                                          event_type, event_key, payload_private, occurred_at)
                VALUES (?, ?, ?, ?, 'AUDIT_TRAIL_TEST', ?, ?, ?, CAST(? AS jsonb), ?)
                """, id, ORG_A, caseId, verifier.id(), UUID.randomUUID(), type, UUID.randomUUID(),
                "{\"marker\":\"" + PAYLOAD_MARKER + "\"}", Timestamp.from(at));
        return id;
    }

    UUID job(UUID eventId, UUID caseId, String status, String lastError, long sequence) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO blockchain_jobs (id, audit_event_id, status, last_error, aid_case_id, chain_sequence,
                                             manifest_private)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, id, eventId, status, lastError, caseId, sequence, "{\"salt\":\"" + MANIFEST_MARKER + "\"}");
        return id;
    }

    void tx(UUID jobId, String txHash, String status, Long block, long chainId) {
        jdbc.update("""
                INSERT INTO blockchain_transactions (blockchain_job_id, tx_hash, status, block_number, receipt_status,
                                                     chain_id, contract_address)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, jobId, txHash, status, block,
                "CONFIRMED".equals(status) ? (Short) (short) 1 : "REVERTED".equals(status) ? (Short) (short) 0 : null,
                chainId, CONTRACT);
    }

    static String hash() {
        return "0x" + HexFormat.of().formatHex(UUID.randomUUID().toString().repeat(2).replace("-", "")
                .substring(0, 32).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    static JsonNode item(JsonNode page, UUID eventId) {
        for (JsonNode item : page.path("items")) {
            if (item.path("eventId").asText().equals(eventId.toString())) return item;
        }
        throw new AssertionError("Event not in trail: " + eventId);
    }

    static JsonNode byType(JsonNode page, String type) {
        for (JsonNode item : page.path("items")) {
            if (item.path("type").asText().equals(type)) return item;
        }
        throw new AssertionError("No event of type " + type);
    }

    static List<String> types(JsonNode page) {
        List<String> types = new ArrayList<>();
        page.path("items").forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    static void assertAbsent(JsonNode node, String... fields) {
        for (String field : fields) {
            assertThat(node.path(field).isNull() || node.path(field).isMissingNode())
                    .as("%s must be absent", field).isTrue();
        }
    }
}
