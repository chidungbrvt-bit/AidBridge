package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

/** S01 contract through HTTP and the isolated PostgreSQL test schema. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DisputeOpenIT extends AbstractDeliveryHttpIT {

    @Test
    void opensCaseLevelDisputeAndReplaysTheOriginalResponseWithCurrentAccess() throws Exception {
        UUID caseId = verifiedCase();
        String key = newKey();
        var body = Map.of("reason", "  The case evidence needs another review.  ");
        var sequencesBefore = sequences(caseId);

        var first = open(verifier, caseId, body, key);
        status(first, 201);
        JsonNode created = node(first);
        UUID disputeId = id(created);
        assertThat(first.headers().firstValue("Location")).contains("/api/disputes/" + disputeId);
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(created.path("caseId").asText()).isEqualTo(caseId.toString());
        assertThat(created.path("status").asText()).isEqualTo("OPEN");
        assertThat(created.path("reason").asText()).isEqualTo("The case evidence needs another review.");
        assertThat(created.has("deliveryId")).isFalse();
        Instant.parse(created.path("createdAt").asText());
        assertThat(created.path("updatedAt")).isEqualTo(created.path("createdAt"));
        assertThat(jdbc.queryForMap("SELECT opened_by, delivery_id, status FROM disputes WHERE id = ?", disputeId))
                .containsEntry("opened_by", verifier.id()).containsEntry("delivery_id", null)
                .containsEntry("status", "OPEN");

        var replay = open(verifier, caseId, body, key);
        status(replay, 201);
        assertThat(node(replay)).isEqualTo(created);
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(open(verifier, caseId, Map.of("reason", "A different issue."), key), 409, "IDEMPOTENCY_CONFLICT");
        jdbc.update("UPDATE organization_memberships SET status = 'DISABLED' WHERE id = ?", verifier.membership());
        problem(open(verifier, caseId, body, key), 404, "RESOURCE_NOT_FOUND");

        assertThat(count("disputes", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(count("audit_events", "event_key", disputeId)).isEqualTo(1);
        assertThat(auditPayload(disputeId).path("caseReopened").asBoolean()).isFalse();
        assertThat(sequences(caseId)).isEqualTo(sequencesBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("VERIFIED");
    }

    @Test
    void rejectsOutOfScopeActorsInvalidInputAndMissingCsrfWithoutWriting() throws Exception {
        UUID caseId = verifiedCase();
        Actor foreignVerifier = actor(ORG_B, "VERIFIER", false);
        var body = Map.of("reason", "The case evidence needs another review.");

        problem(open(volunteer, caseId, body, newKey()), 403, "FORBIDDEN");
        problem(open(foreignVerifier, caseId, body, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(open(admin, caseId, body, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(open(verifier, caseId, Map.of("reason", "   "), newKey()), 400, "VALIDATION_ERROR");
        problem(open(verifier, caseId, body, null), 400, "VALIDATION_ERROR");
        problem(verifier.browser().send("POST", route(caseId), body, newKey(), false), 403, "CSRF_INVALID");

        assertThat(count("disputes", "aid_case_id", caseId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'DISPUTE_OPENED'
                """, Long.class, caseId)).isZero();
    }

    @Test
    void onlyAcceptsAnEligibleDeliveryInTheCaseAndBlocksNewOperationalWrites() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Stock stock = stockedCase(orgAdmin);
        Stock otherCase = stockedCase(orgAdmin);
        UUID deliveryId = record(stock, 2, 1);
        UUID pendingId = record(stock, 1, 1);
        UUID otherDeliveryId = record(otherCase, 1, 1);
        var failedDelivery = deliver(volunteer, stock.caseId(), failed("Recipient unavailable"), newKey());
        status(failedDelivery, 201);
        UUID failedId = id(node(failedDelivery));
        status(confirm(reviewer, stock, deliveryId), 200);

        for (UUID invalidId : List.of(otherDeliveryId, failedId, UUID.randomUUID())) {
            problem(open(reviewer, stock.caseId(), deliveryDispute(invalidId), newKey()), 404, "RESOURCE_NOT_FOUND");
        }
        assertThat(count("disputes", "aid_case_id", stock.caseId())).isZero();

        var opened = open(reviewer, stock.caseId(), deliveryDispute(deliveryId), newKey());
        status(opened, 201);
        UUID disputeId = id(node(opened));
        assertThat(node(opened).path("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(auditPayload(disputeId).path("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        problem(deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.rice(), 1), newKey()),
                409, "DISPUTE_OPEN");
        problem(confirm(reviewer, stock, pendingId), 409, "DISPUTE_OPEN");
        assertThat(count("delivery_confirmations", "delivery_id", pendingId)).isZero();
    }

    @Test
    void reopensCompletedCaseAndRetainsThePreviousClosureInAudit() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        var contributions = jdbc.queryForList("""
                SELECT id, contribution_id, campaign_item_id FROM contribution_items
                WHERE campaign_item_id IN (?, ?)
                """, stock.rice(), stock.water());
        UUID commitmentId = (UUID) contributions.getFirst().get("contribution_id");
        var receiptLines = contributions.stream().map(line -> Map.of(
                "contributionItemId", line.get("id"),
                "quantity", stock.rice().equals(line.get("campaign_item_id")) ? 4 : 2)).toList();
        status(orgAdmin.browser().send("POST", "/api/commitments/" + commitmentId + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", receiptLines), newKey(), true), 201);
        UUID deliveryId = record(stock, 10, 4);
        status(confirm(actor(ORG_A, "DELIVERY_CONFIRMER", false), stock, deliveryId), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, stock.campaignId()))
                .isEqualTo("FULFILLED");

        // Supply the final P05 state as a fixture until the close endpoint is implemented.
        var closedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE campaigns SET status = 'CLOSED', closed_at = ? WHERE id = ?", closedAt, stock.campaignId());
        jdbc.update("UPDATE aid_cases SET status = 'COMPLETED' WHERE id = ?", stock.caseId());
        var sequencesBefore = sequences(stock.caseId());

        var opened = open(orgAdmin, stock.caseId(), deliveryDispute(deliveryId), newKey());
        status(opened, 201);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, stock.caseId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForMap("SELECT status, closed_at FROM campaigns WHERE id = ?", stock.campaignId()))
                .containsEntry("status", "FULFILLED").containsEntry("closed_at", null);
        JsonNode payload = auditPayload(id(node(opened)));
        assertThat(payload.path("caseReopened").asBoolean()).isTrue();
        assertThat(Instant.parse(payload.path("previousClosedAt").asText())).isEqualTo(closedAt.toInstant());
        assertThat(sequences(stock.caseId())).isEqualTo(sequencesBefore);
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
    }

    private HttpResponse<String> open(Actor actor, UUID caseId, Object body, String key) throws Exception {
        return actor.browser().send("POST", route(caseId), body, key, true);
    }

    private HttpResponse<String> confirm(Actor reviewer, Stock stock, UUID deliveryId) throws Exception {
        return reviewer.browser().send("POST", "/api/deliveries/" + deliveryId + "/confirm",
                Map.of("decision", "CONFIRMED", "reviewedEvidenceVersionIds", stock.evidenceIds()), newKey(), true);
    }

    private UUID record(Stock stock, int rice, int water) throws Exception {
        var response = deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), rice, stock.water(), water), newKey());
        status(response, 201);
        return id(node(response));
    }

    private JsonNode auditPayload(UUID disputeId) {
        return json.readTree(jdbc.queryForObject("""
                SELECT payload_private::text FROM audit_events
                WHERE event_type = 'DISPUTE_OPENED' AND event_key = ?
                """, String.class, disputeId));
    }

    private static Map<String, Object> deliveryDispute(UUID deliveryId) {
        return Map.of("deliveryId", deliveryId, "reason", "The recorded handover needs review.");
    }

    private static String route(UUID caseId) {
        return "/api/cases/" + caseId + "/disputes";
    }

    private static UUID id(JsonNode response) {
        return UUID.fromString(response.path("id").asText());
    }
}
