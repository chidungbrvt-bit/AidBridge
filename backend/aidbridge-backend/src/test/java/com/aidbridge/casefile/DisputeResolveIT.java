package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

/** S03 through HTTP and the isolated PostgreSQL test schema. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DisputeResolveIT extends AbstractDeliveryHttpIT {

    @Test
    void upholdsCaseDisputeAndReplaysOnlyForACurrentPlatformAdmin() throws Exception {
        UUID caseId = verifiedCase();
        UUID disputeId = open(verifier, caseId, null);
        var body = decision("UPHOLD", List.of());
        String key = newKey();
        var sequenceBefore = sequences(caseId);

        var first = resolve(admin, disputeId, body, key);
        status(first, 200);
        JsonNode response = node(first);
        assertThat(id(response)).isEqualTo(disputeId);
        assertThat(response.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(response.path("resolution").asText()).isEqualTo("UPHOLD");
        assertThat(response.path("reason").asText()).isEqualTo("The recorded facts need review.");
        assertThat(Instant.parse(response.path("resolvedAt").asText())).isNotNull();
        assertThat(jdbc.queryForMap("SELECT status, resolved_by, resolution_note FROM disputes WHERE id = ?", disputeId))
                .containsEntry("status", "RESOLVED").containsEntry("resolved_by", admin.id())
                .containsEntry("resolution_note", body.get("reason"));
        assertThat(first.headers().firstValue("Location")).isEmpty();

        var replay = resolve(admin, disputeId, body, key);
        status(replay, 200);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(resolve(admin, disputeId, body, newKey()), 409, "INVALID_STATE");
        problem(resolve(admin, disputeId, Map.of("outcome", "UPHOLD", "reason", "A different reason.",
                "reviewEvidenceVersionIds", List.of()), key), 409, "IDEMPOTENCY_CONFLICT");

        jdbc.update("UPDATE users SET platform_role = 'USER' WHERE id = ?", admin.id());
        problem(resolve(admin, disputeId, body, key), 403, "FORBIDDEN");
        assertThat(eventCount(caseId, "DISPUTE_RESOLVED")).isEqualTo(1);
        assertThat(count("delivery_voids", "aid_case_id", caseId)).isZero();
        assertThat(sequences(caseId)).isEqualTo(sequenceBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("VERIFIED");
    }

    @Test
    void upholdKeepsReservationsAndRequiresIndependentConfirmationAfterTheLastDispute() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 4, 1);
        UUID firstDispute = open(verifier, stock.caseId(), deliveryId);
        UUID secondDispute = open(verifier, stock.caseId(), deliveryId);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        assertThat(stock.evidenceIds()).hasSizeGreaterThan(1);
        var body = decision("UPHOLD", List.of(stock.evidenceIds().getFirst()));
        var sequenceBefore = sequences(stock.caseId());
        JsonNode before = readCase(stock.caseId());

        status(resolve(admin, firstDispute, body, newKey()), 200);
        assertThat(reviewStatus(readCase(stock.caseId()), deliveryId)).isEqualTo("REQUIRES_REVIEW");
        problem(confirm(reviewer, stock, deliveryId), 409, "DISPUTE_OPEN");

        status(resolve(admin, secondDispute, body, newKey()), 200);
        JsonNode after = readCase(stock.caseId());
        assertThat(reviewStatus(after, deliveryId)).isEqualTo("PENDING");
        assertThat(after.path("goodsTotals")).isEqualTo(before.path("goodsTotals"));
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isZero();
        assertThat(count("delivery_voids", "delivery_id", deliveryId)).isZero();
        assertThat(eventCount(stock.caseId(), "DISPUTE_RESOLVED")).isEqualTo(2);
        assertThat(sequences(stock.caseId())).isEqualTo(sequenceBefore);
        status(confirm(reviewer, stock, deliveryId), 200);
    }

    @Test
    void voidRetainsHistoryReleasesOnlyThatReservationAndReopensFulfillment() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Stock stock = stockedCase(orgAdmin);
        receiveRemainder(orgAdmin, stock);
        UUID voidedDelivery = record(stock, 4, 1);
        UUID keptDelivery = record(stock, 6, 3);
        status(confirm(reviewer, stock, voidedDelivery), 200);
        status(confirm(reviewer, stock, keptDelivery), 200);
        assertThat(campaignStatus(stock)).isEqualTo("FULFILLED");
        UUID disputeId = open(orgAdmin, stock.caseId(), voidedDelivery);
        UUID anotherDispute = open(orgAdmin, stock.caseId(), voidedDelivery);
        var sequenceBefore = sequences(stock.caseId());
        String key = newKey();
        var body = decision("VOID_DELIVERY", stock.evidenceIds());

        var first = resolve(admin, disputeId, body, key);
        status(first, 200);
        JsonNode response = node(first);
        assertThat(response.path("resolution").asText()).isEqualTo("VOID_DELIVERY");
        assertThat(response.path("status").asText()).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForMap("SELECT dispute_id, voided_by, reason FROM delivery_voids WHERE delivery_id = ?",
                voidedDelivery)).containsEntry("dispute_id", disputeId).containsEntry("voided_by", admin.id())
                .containsEntry("reason", body.get("reason"));

        var replay = resolve(admin, disputeId, body, key);
        status(replay, 200);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(resolve(admin, anotherDispute, body, newKey()), 409, "INVALID_STATE");
        assertThat(jdbc.queryForObject("SELECT status FROM disputes WHERE id = ?", String.class, anotherDispute))
                .isEqualTo("OPEN");

        JsonNode refreshed = readCase(stock.caseId());
        totals(refreshed, stock.rice(), 10, 6, 6, 4);
        totals(refreshed, stock.water(), 4, 3, 3, 1);
        assertThat(reviewStatus(refreshed, voidedDelivery)).isEqualTo("VOIDED");
        assertThat(reviewStatus(refreshed, keptDelivery)).isEqualTo("CONFIRMED");
        assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");
        assertThat(refreshed.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(2);
        assertThat(count("delivery_items", "delivery_id", voidedDelivery)).isEqualTo(2);
        assertThat(count("delivery_confirmations", "delivery_id", voidedDelivery)).isEqualTo(1);
        assertThat(count("delivery_voids", "delivery_id", voidedDelivery)).isEqualTo(1);
        assertThat(eventCount(stock.caseId(), "DELIVERY_VOIDED")).isEqualTo(1);
        assertThat(eventCount(stock.caseId(), "DISPUTE_RESOLVED")).isEqualTo(1);
        assertThat(sequences(stock.caseId())).isEqualTo(sequenceBefore);
    }

    @Test
    void voidedDeliveryIsReplacedByANewDeliveryRequiringIndependentConfirmation() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Stock stock = stockedCase(orgAdmin);
        UUID originalId = record(stock, 4, 1);
        status(confirm(reviewer, stock, originalId), 200);
        var replacementBody = delivered(stock.evidenceIds(), stock.rice(), 4, stock.water(), 1);

        problem(deliver(volunteer, stock.caseId(), replacementBody, newKey()), 409, "QUANTITY_EXCEEDED");
        UUID disputeId = open(orgAdmin, stock.caseId(), originalId);
        status(resolve(admin, disputeId, decision("VOID_DELIVERY", stock.evidenceIds()), newKey()), 200);

        JsonNode voided = readCase(stock.caseId());
        assertThat(reviewStatus(voided, originalId)).isEqualTo("VOIDED");
        totals(voided, stock.rice(), 6, 0, 0, 6);
        totals(voided, stock.water(), 2, 0, 0, 2);
        problem(confirm(reviewer, stock, originalId), 409, "INVALID_STATE");

        var replacement = deliver(volunteer, stock.caseId(), replacementBody, newKey());
        status(replacement, 201);
        UUID replacementId = id(node(replacement));
        assertThat(replacementId).isNotEqualTo(originalId);
        assertThat(node(replacement).path("reviewStatus").asText()).isEqualTo("PENDING");
        JsonNode pending = readCase(stock.caseId());
        assertThat(reviewStatus(pending, replacementId)).isEqualTo("PENDING");
        totals(pending, stock.rice(), 6, 4, 0, 2);
        totals(pending, stock.water(), 2, 1, 0, 1);
        assertThat(count("delivery_confirmations", "delivery_id", replacementId)).isZero();

        jdbc.update("INSERT INTO membership_roles (membership_id, role) VALUES (?, 'DELIVERY_CONFIRMER')",
                volunteer.membership());
        problem(confirm(volunteer, stock, replacementId), 403, "SELF_CONFIRMATION");
        assertThat(count("delivery_confirmations", "delivery_id", replacementId)).isZero();
        status(confirm(reviewer, stock, replacementId), 200);

        JsonNode confirmed = readCase(stock.caseId());
        assertThat(reviewStatus(confirmed, originalId)).isEqualTo("VOIDED");
        assertThat(reviewStatus(confirmed, replacementId)).isEqualTo("CONFIRMED");
        totals(confirmed, stock.rice(), 6, 4, 4, 2);
        totals(confirmed, stock.water(), 2, 1, 1, 1);
        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(2);
        assertThat(count("delivery_items", "delivery_id", originalId)).isEqualTo(2);
        assertThat(count("delivery_confirmations", "delivery_id", originalId)).isEqualTo(1);
        assertThat(count("delivery_confirmations", "delivery_id", replacementId)).isEqualTo(1);
        assertThat(count("delivery_voids", "delivery_id", originalId)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT entity_id FROM audit_events WHERE aid_case_id = ? AND event_type = 'DELIVERY_RECORDED'
                """, UUID.class, stock.caseId())).containsExactlyInAnyOrder(originalId, replacementId);
        assertThat(sequences(stock.caseId())).containsExactly(
                "CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3",
                "DELIVERY_RECORDED:4", "DELIVERY_CONFIRMED:5", "DELIVERY_RECORDED:6", "DELIVERY_CONFIRMED:7");
    }

    @Test
    void rejectsUnauthorizedInvalidAndOutOfSnapshotRequestsWithoutResolving() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        UUID deliveryId = record(stock, 1, 1);
        UUID caseDispute = open(orgAdmin, stock.caseId(), null);
        UUID deliveryDispute = open(orgAdmin, stock.caseId(), deliveryId);
        var validBody = decision("UPHOLD", List.of());
        UUID evidenceId = stock.evidenceIds().getFirst();

        problem(resolve(orgAdmin, deliveryDispute, validBody, newKey()), 403, "FORBIDDEN");
        problem(resolve(admin, UUID.randomUUID(), validBody, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(admin.browser().send("POST", route(deliveryDispute), validBody, newKey(), false), 403, "CSRF_INVALID");
        problem(resolve(admin, deliveryDispute, validBody, null), 400, "VALIDATION_ERROR");
        problem(resolve(admin, caseDispute, decision("VOID_DELIVERY", List.of()), newKey()), 400, "VALIDATION_ERROR");
        problem(resolve(admin, caseDispute, decision("UPHOLD", List.of(evidenceId)), newKey()), 400, "VALIDATION_ERROR");
        problem(resolve(admin, deliveryDispute, decision("VOID_DELIVERY", List.of(UUID.randomUUID())), newKey()),
                400, "VALIDATION_ERROR");
        problem(resolve(admin, deliveryDispute, decision("UPHOLD", List.of(evidenceId, evidenceId)), newKey()),
                400, "VALIDATION_ERROR");
        problem(resolve(admin, deliveryDispute, Map.of("outcome", "UPHOLD", "reason", " ",
                "reviewEvidenceVersionIds", List.of()), newKey()), 400, "VALIDATION_ERROR");

        assertThat(jdbc.queryForList("SELECT status FROM disputes WHERE id IN (?, ?)", String.class,
                caseDispute, deliveryDispute)).containsExactly("OPEN", "OPEN");
        assertThat(count("delivery_voids", "aid_case_id", stock.caseId())).isZero();
        assertThat(eventCount(stock.caseId(), "DISPUTE_RESOLVED")).isZero();
    }

    @Test
    void twoConcurrentResolutionsCommitOneDecision() throws Exception {
        UUID caseId = verifiedCase();
        UUID disputeId = open(verifier, caseId, null);
        Actor secondAdmin = actor(null, null, true);
        var body = decision("UPHOLD", List.of());
        String firstKey = newKey();
        String secondKey = newKey();

        var results = race(List.of(
                () -> resolve(admin, disputeId, body, firstKey),
                () -> resolve(secondAdmin, disputeId, body, secondKey)));
        assertThat(results).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        int winner = results.getFirst().statusCode() == 200 ? 0 : 1;
        status(results.get(winner), 200);
        problem(results.get(1 - winner), 409, "INVALID_STATE");
        UUID expectedActor = winner == 0 ? admin.id() : secondAdmin.id();
        assertThat(jdbc.queryForObject("SELECT resolved_by FROM disputes WHERE id = ?", UUID.class, disputeId))
                .isEqualTo(expectedActor);
        assertThat(eventCount(caseId, "DISPUTE_RESOLVED")).isEqualTo(1);
    }

    private void receiveRemainder(Actor orgAdmin, Stock stock) throws Exception {
        var lines = jdbc.queryForList("""
                SELECT id, contribution_id, campaign_item_id FROM contribution_items
                WHERE campaign_item_id IN (?, ?)
                """, stock.rice(), stock.water());
        UUID contributionId = (UUID) lines.getFirst().get("contribution_id");
        var items = lines.stream().map(line -> Map.of(
                "contributionItemId", line.get("id"),
                "quantity", stock.rice().equals(line.get("campaign_item_id")) ? 4 : 2)).toList();
        status(orgAdmin.browser().send("POST", "/api/commitments/" + contributionId + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", items), newKey(), true), 201);
    }

    private UUID record(Stock stock, int rice, int water) throws Exception {
        var response = deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), rice, stock.water(), water), newKey());
        status(response, 201);
        return id(node(response));
    }

    private UUID open(Actor actor, UUID caseId, UUID deliveryId) throws Exception {
        String reason = "The recorded facts need review.";
        var body = deliveryId == null ? Map.of("reason", reason) : Map.of("reason", reason, "deliveryId", deliveryId);
        var response = actor.browser().send("POST", "/api/cases/" + caseId + "/disputes", body, newKey(), true);
        status(response, 201);
        return id(node(response));
    }

    private HttpResponse<String> confirm(Actor reviewer, Stock stock, UUID deliveryId) throws Exception {
        return reviewer.browser().send("POST", "/api/deliveries/" + deliveryId + "/confirm",
                Map.of("decision", "CONFIRMED", "reviewedEvidenceVersionIds", stock.evidenceIds()), newKey(), true);
    }

    private HttpResponse<String> resolve(Actor actor, UUID disputeId, Object body, String key) throws Exception {
        return actor.browser().send("POST", route(disputeId), body, key, true);
    }

    private JsonNode readCase(UUID caseId) throws Exception {
        var response = volunteer.browser().send("GET", "/api/org/cases/" + caseId, null, null, false);
        status(response, 200);
        return node(response);
    }

    private String campaignStatus(Stock stock) {
        return jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, stock.campaignId());
    }

    private long eventCount(UUID caseId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = ?",
                Long.class, caseId, type);
    }

    private static String route(UUID disputeId) {
        return "/api/disputes/" + disputeId + "/resolve";
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
    }

    private static Map<String, Object> decision(String outcome, List<UUID> ids) {
        return Map.of("outcome", outcome, "reason", "The evidence has been reviewed.", "reviewEvidenceVersionIds", ids);
    }

    private static String reviewStatus(JsonNode detail, UUID deliveryId) {
        return find(detail.path("deliveries"), "id", deliveryId).path("reviewStatus").asText();
    }

    private static void totals(JsonNode detail, UUID itemId, long received, long delivered, long confirmed, long available) {
        var item = find(detail.path("goodsTotals"), "campaignItemId", itemId);
        assertThat(item.path("receivedQty").asLong()).isEqualTo(received);
        assertThat(item.path("deliveredQty").asLong()).isEqualTo(delivered);
        assertThat(item.path("confirmedQty").asLong()).isEqualTo(confirmed);
        assertThat(item.path("availableToDeliverQty").asLong()).isEqualTo(available);
    }

    private static JsonNode find(JsonNode entries, String field, UUID id) {
        for (JsonNode entry : entries) {
            if (id.toString().equals(entry.path(field).asText())) {
                return entry;
            }
        }
        throw new AssertionError("Missing " + field + " " + id);
    }
}
