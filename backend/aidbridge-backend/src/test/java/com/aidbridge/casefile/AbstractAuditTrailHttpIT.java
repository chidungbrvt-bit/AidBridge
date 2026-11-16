package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Shared fixtures for T01/T02: audit events, jobs and attempts seeded with JDBC in the isolated test schema. */
abstract class AbstractAuditTrailHttpIT extends AbstractCampaignHttpIT {
    static final String CONTRACT = "0x015D941c679b0c402c8262BE237adAD36cEf6726";
    static final String PAYLOAD_MARKER = "PRIVATE_PAYLOAD_MARKER";
    static final String MANIFEST_MARKER = "PRIVATE_MANIFEST_MARKER";
    static final String SNOWTRACE = "https://testnet.snowtrace.io/tx/";

    // ---- fixtures (test schema only) ---------------------------------------------------------------------

    UUID event(UUID caseId, String type, Instant at) {
        return event(caseId, type, at, verifier.id());
    }

    UUID event(UUID caseId, String type, Instant at, UUID actorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_events (id, organization_id, aid_case_id, actor_user_id, entity_type, entity_id,
                                          event_type, event_key, payload_private, occurred_at)
                VALUES (?, ?, ?, ?, 'AUDIT_TRAIL_TEST', ?, ?, ?, CAST(? AS jsonb), ?)
                """, id, ORG_A, caseId, actorId, UUID.randomUUID(), type, UUID.randomUUID(),
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

    /** Same opaque public id as AuditTrailService (T02): never the internal audit UUID. */
    static String publicAuditId(Object eventId) {
        return "audit_" + com.aidbridge.shared.util.Hashing.sha256(
                ("public-audit:" + eventId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static JsonNode item(JsonNode page, UUID eventId) {
        for (JsonNode item : page.path("items")) {
            if ((item.path("eventId").asText().equals(eventId.toString()) || item.path("eventId").asText().equals(publicAuditId(eventId)))) return item;
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

    String publicPath(UUID campaign) {
        var refs=jdbc.queryForList("SELECT '0x'||encode(c.public_ref,'hex') FROM aid_cases c JOIN campaigns p ON p.aid_case_id=c.id WHERE p.id=?",String.class,campaign);
        return "/api/public/cases/"+(refs.isEmpty()?"0x"+"ff".repeat(32):refs.get(0))+"/audit";
    }
}
