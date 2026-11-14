package com.aidbridge.casefile;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;

/** Stock fixtures for D01 tests: pledge (G01) and receipt (R01) through the real API. */
abstract class AbstractDeliveryHttpIT extends AbstractCampaignHttpIT {
    static final List<Map<String, Object>> RICE_WATER = List.of(
            Map.of("name", "Rice", "unit", "BAG", "quantity", 10),
            Map.of("name", "Water", "unit", "BOX", "quantity", 4));

    record Stock(UUID caseId, UUID campaignId, UUID rice, UUID water, List<UUID> evidenceIds) {
    }

    /** Published campaign with Rice 10 / Water 4 pledged and Rice 6 / Water 2 received (available 6 / 2). */
    Stock stockedCase(Actor orgAdmin) throws Exception {
        UUID campaignId = publishedCampaign(orgAdmin, RICE_WATER);
        List<UUID> items = campaignItemIds(campaignId); // ordered by name: Rice, Water
        UUID rice = items.get(0), water = items.get(1);

        var pledged = donor(true).browser().send("POST", "/api/campaigns/" + campaignId + "/commitments",
                Map.of("items", List.of(
                        Map.of("campaignItemId", rice, "quantity", 10),
                        Map.of("campaignItemId", water, "quantity", 4))),
                newKey(), true);
        status(pledged, 201);
        JsonNode commitment = node(pledged);

        var received = orgAdmin.browser().send("POST",
                "/api/commitments/" + commitment.path("id").asText() + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", List.of(
                        Map.of("contributionItemId", lineFor(commitment, rice), "quantity", 6),
                        Map.of("contributionItemId", lineFor(commitment, water), "quantity", 2))),
                newKey(), true);
        status(received, 201);

        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        List<UUID> evidence = jdbc.queryForList("""
                SELECT evidence_version_id FROM case_evidence_links
                WHERE aid_case_id = ? AND retired_at_version IS NULL
                ORDER BY evidence_version_id
                """, UUID.class, caseId);
        return new Stock(caseId, campaignId, rice, water, evidence);
    }

    HttpResponse<String> deliver(Actor actor, UUID caseId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/cases/" + caseId + "/deliveries", body, key, true);
    }

    static Map<String, Object> delivered(List<UUID> evidenceIds, Object... pairs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            items.add(Map.of("campaignItemId", pairs[i], "quantity", pairs[i + 1]));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", "DELIVERED");
        body.put("items", items);
        body.put("evidenceVersionIds", evidenceIds);
        return body;
    }

    static Map<String, Object> failed(String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("outcome", "FAILED");
        body.put("items", List.of());
        body.put("evidenceVersionIds", List.of());
        body.put("reason", reason);
        return body;
    }

    long deliveredQty(UUID campaignItemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(di.delivered_quantity), 0)
                FROM delivery_items di JOIN deliveries d ON d.id = di.delivery_id
                WHERE di.campaign_item_id = ? AND d.outcome = 'DELIVERED'
                """, Long.class, campaignItemId);
    }

    List<String> sequences(UUID caseId) {
        return jdbc.queryForList("""
                SELECT a.event_type || ':' || j.chain_sequence
                FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE j.aid_case_id = ?
                ORDER BY j.chain_sequence
                """, String.class, caseId);
    }

    static String lineFor(JsonNode commitment, UUID campaignItemId) {
        for (JsonNode line : commitment.path("items")) {
            if (line.path("campaignItemId").asText().equals(campaignItemId.toString())) {
                return line.path("id").asText();
            }
        }
        throw new AssertionError("No commitment line for " + campaignItemId);
    }

    /** Releases every call at the same instant; invariants are checked in the database afterwards. */
    static List<HttpResponse<String>> race(List<Callable<HttpResponse<String>>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (var call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<HttpResponse<String>> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        }
    }
}