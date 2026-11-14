package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DeliveryApiIT extends AbstractDeliveryHttpIT {

    @Test
    void deliveredWritesLinesEvidenceAuditAndOneJobAndReplaysOnce() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        String key = newKey();
        var body = delivered(stock.evidenceIds(), stock.rice(), 4, stock.water(), 1);

        var first = deliver(volunteer, stock.caseId(), body, key);
        status(first, 201);
        JsonNode delivery = node(first);
        UUID deliveryId = UUID.fromString(delivery.path("id").asText());
        assertThat(first.headers().firstValue("Location")).contains("/api/deliveries/" + deliveryId);
        assertThat(delivery.path("outcome").asText()).isEqualTo("DELIVERED");
        assertThat(delivery.path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(delivery.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(delivery.path("recorderId").asText()).isEqualTo(volunteer.id().toString());
        assertThat(delivery.path("items").size()).isEqualTo(2);
        assertThat(delivery.path("evidenceVersionIds").size()).isEqualTo(stock.evidenceIds().size());

        assertThat(count("delivery_items", "delivery_id", deliveryId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM delivery_evidence_links
                WHERE delivery_id = ? AND purpose = 'HANDOVER_PROOF' AND linked_by = ?
                """, Long.class, deliveryId, volunteer.id())).isEqualTo(stock.evidenceIds().size());
        assertThat(jdbc.queryForMap("""
                SELECT a.event_key, j.status FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ?
                """, UUID.fromString(delivery.path("auditEventId").asText())))
                .containsEntry("event_key", deliveryId).containsEntry("status", "PENDING");
        String payload = jdbc.queryForObject("""
                SELECT payload_private::text FROM audit_events WHERE event_key = ?
                """, String.class, deliveryId);
        assertThat(json.readTree(payload).path("deliveryRef").asText()).matches("0x[0-9a-f]{64}");

        var replay = deliver(volunteer, stock.caseId(), body, key);
        status(replay, 201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(node(replay)).isEqualTo(delivery);
        problem(deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), 3), key), 409, "IDEMPOTENCY_CONFLICT");

        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(1);
        assertThat(sequences(stock.caseId())).containsExactly(
                "CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3", "DELIVERY_RECORDED:4");
    }

    @Test
    void anUnconfirmedDeliveryAlreadyConsumesStock() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        status(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 4), newKey()), 201);
        // Rice: received 6, pending delivery 4 -> available 2, although nothing is confirmed.
        problem(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 3), newKey()),
                409, "QUANTITY_EXCEEDED");
        status(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 2), newKey()), 201);
        problem(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 1), newKey()),
                409, "QUANTITY_EXCEEDED");
        assertThat(deliveredQty(stock.rice())).isEqualTo(6);
    }

    @Test
    void failedAttemptRequiresAReasonAndHasZeroStockEffectAndNoChainJob() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));

        problem(deliver(volunteer, stock.caseId(), failed(null), newKey()), 400, "VALIDATION_ERROR");
        var withItems = failed("Recipient not at home");
        withItems.put("items", List.of(Map.of("campaignItemId", stock.rice(), "quantity", 1)));
        problem(deliver(volunteer, stock.caseId(), withItems, newKey()), 400, "VALIDATION_ERROR");

        var response = deliver(volunteer, stock.caseId(), failed("Recipient not at home"), newKey());
        status(response, 201);
        JsonNode attempt = node(response);
        assertThat(attempt.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(attempt.path("reviewStatus").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(attempt.path("blockchainStatus").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(attempt.path("auditUrl").isNull()).isTrue();
        assertThat(attempt.path("items").size()).isZero();

        UUID attemptId = UUID.fromString(attempt.path("id").asText());
        assertThat(count("delivery_items", "delivery_id", attemptId)).isZero();
        assertThat(jdbc.queryForObject("SELECT event_type FROM audit_events WHERE event_key = ?",
                String.class, attemptId)).isEqualTo("DELIVERY_ATTEMPT_FAILED");
        assertThat(sequences(stock.caseId())).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3");
        // The failed attempt reserved nothing: the full received quantity can still be delivered.
        status(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 6), newKey()), 201);
    }

    @Test
    void evidenceItemsAndHandoverTimeMustBelongToThisCase() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        Stock otherCase = stockedCase(orgAdmin);

        problem(deliver(volunteer, stock.caseId(),
                delivered(otherCase.evidenceIds(), stock.rice(), 1), newKey()), 400, "VALIDATION_ERROR");
        problem(deliver(volunteer, stock.caseId(),
                delivered(List.of(UUID.randomUUID()), stock.rice(), 1), newKey()), 400, "VALIDATION_ERROR");
        problem(deliver(volunteer, stock.caseId(),
                delivered(List.of(), stock.rice(), 1), newKey()), 400, "VALIDATION_ERROR");
        problem(deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), otherCase.rice(), 1), newKey()), 409, "INVALID_STATE");
        var future = delivered(stock.evidenceIds(), stock.rice(), 1);
        future.put("handedOverAt", Instant.now().plusSeconds(600).toString());
        problem(deliver(volunteer, stock.caseId(), future, newKey()), 400, "VALIDATION_ERROR");

        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isZero();
        assertThat(deliveredQty(stock.rice())).isZero();
    }

    @Test
    void onlyVolunteersOfTheOwningOrganizationMayRecordAndReplayIsReauthorized() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        var body = delivered(stock.evidenceIds(), stock.rice(), 1);

        problem(deliver(verifier, stock.caseId(), body, newKey()), 403, "FORBIDDEN");
        problem(deliver(orgAdmin, stock.caseId(), body, newKey()), 403, "FORBIDDEN");
        problem(deliver(other, stock.caseId(), body, newKey()), 404, "RESOURCE_NOT_FOUND"); // ORG_B volunteer
        problem(deliver(admin, stock.caseId(), body, newKey()), 404, "RESOURCE_NOT_FOUND"); // platform admin

        String key = newKey();
        status(deliver(volunteer, stock.caseId(), body, key), 201);
        jdbc.update("UPDATE membership_roles SET role = 'VERIFIER' WHERE membership_id = ?", volunteer.membership());
        problem(deliver(volunteer, stock.caseId(), body, key), 403, "FORBIDDEN");
        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(1);
    }

    @Test
    void replayReturnsTheOriginalWhileAFreshWriteSeesTheOpenDispute() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        var body = delivered(stock.evidenceIds(), stock.rice(), 1);
        String key = newKey();
        var first = deliver(volunteer, stock.caseId(), body, key);
        status(first, 201);

        jdbc.update("INSERT INTO disputes (aid_case_id, opened_by, status, reason) VALUES (?, ?, 'OPEN', 'Synthetic')",
                stock.caseId(), orgAdmin.id());
        var replay = deliver(volunteer, stock.caseId(), body, key);
        status(replay, 201);
        assertThat(node(replay)).isEqualTo(node(first));
        problem(deliver(volunteer, stock.caseId(), body, newKey()), 409, "DISPUTE_OPEN");
        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(1);
    }

    @Test
    void publicCampaignShowsDeliveredIncludingUnconfirmedButNotFailed() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        status(deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), 4, stock.water(), 1), newKey()), 201);
        status(deliver(volunteer, stock.caseId(), failed("Recipient not at home"), newKey()), 201);

        var campaign = new Browser().send("GET", "/api/campaigns/" + stock.campaignId(), null, null, false);
        status(campaign, 200);
        Map<String, String> progress = new HashMap<>();
        for (JsonNode item : node(campaign).path("items")) {
            progress.put(item.path("name").asText(), String.join("|",
                    item.path("pledgedQty").asText(), item.path("receivedQty").asText(),
                    item.path("deliveredQty").asText(), item.path("confirmedQty").asText()));
        }
        // pledged|received|delivered|confirmed. FAILED adds nothing; confirmation is B10.
        assertThat(progress).containsEntry("Rice", "10|6|4|0").containsEntry("Water", "4|2|1|0");
    }
}