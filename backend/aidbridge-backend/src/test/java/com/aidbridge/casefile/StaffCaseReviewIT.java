package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

/** C03 private read projection. Real HTTP and PostgreSQL in the inherited isolated test schema. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class StaffCaseReviewIT extends AbstractDeliveryHttpIT {

    @Test
    void caseWithoutCampaignKeepsFlatCaseFieldsAndReturnsEmptyCollections() throws Exception {
        JsonNode created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        JsonNode detail = read(volunteer, caseId);
        assertThat(detail.path("id").asText()).isEqualTo(caseId.toString());
        assertThat(detail.path("organizationId").asText()).isEqualTo(ORG_A.toString());
        assertThat(detail.path("internalTitle")).isEqualTo(created.path("internalTitle"));
        assertThat(detail.path("needs")).isEqualTo(created.path("needs"));
        assertThat(detail.has("caseDetail")).isFalse();
        for (String field : List.of("outstandingCommitments", "goodsTotals", "deliveries", "disputes")) {
            assertThat(detail.path(field).isArray()).as(field).isTrue();
            assertThat(detail.path(field).size()).as(field).isZero();
        }
    }

    @Test
    void pendingAndFailedDeliveriesExposeTheirOwnSnapshotsAndPreserveOutstandingCommitments() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        JsonNode before = read(volunteer, stock.caseId());
        assertThat(before.path("outstandingCommitments").size()).isEqualTo(1);
        JsonNode outstanding = before.path("outstandingCommitments").get(0).path("items");
        assertThat(one(outstanding, "campaignItemId", stock.rice()).path("outstandingQty").asLong()).isEqualTo(4);
        assertThat(one(outstanding, "campaignItemId", stock.water()).path("outstandingQty").asLong()).isEqualTo(2);
        assertThat(stock.evidenceIds()).hasSizeGreaterThan(1);
        List<UUID> snapshot = List.of(stock.evidenceIds().getFirst());
        UUID pendingId = record(stock, snapshot, 4, 1);
        var failed = deliver(volunteer, stock.caseId(), failed("Recipient unavailable"), newKey());
        status(failed, 201);
        UUID failedId = id(node(failed));
        var chainsBeforeRead = sequences(stock.caseId());
        long auditBeforeRead = count("audit_events", "aid_case_id", stock.caseId());

        JsonNode detail = read(actor(ORG_A, "DELIVERY_CONFIRMER", false), stock.caseId());
        assertThat(detail.path("outstandingCommitments")).isEqualTo(before.path("outstandingCommitments"));
        assertThat(detail.path("deliveries").size()).isEqualTo(2);
        JsonNode pending = one(detail.path("deliveries"), "id", pendingId);
        assertThat(pending.path("outcome").asText()).isEqualTo("DELIVERED");
        assertThat(pending.path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.path("recorderId").asText()).isEqualTo(volunteer.id().toString());
        assertThat(pending.path("recordedAt").asText()).isNotBlank();
        assertThat(pending.path("items").size()).isEqualTo(2);
        assertThat(one(pending.path("items"), "campaignItemId", stock.rice()).path("quantity").asInt()).isEqualTo(4);
        assertThat(one(pending.path("items"), "campaignItemId", stock.water()).path("quantity").asInt()).isEqualTo(1);
        // Only the frozen D01 selection, not every currently linked case evidence version.
        assertThat(pending.path("evidenceVersionIds").size()).isEqualTo(1);
        assertThat(pending.path("evidenceVersionIds").get(0).asText()).isEqualTo(snapshot.getFirst().toString());
        JsonNode attempt = one(detail.path("deliveries"), "id", failedId);
        assertThat(attempt.path("outcome").asText()).isEqualTo("FAILED");
        assertThat(attempt.path("reviewStatus").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(attempt.path("items").size()).isZero();
        assertThat(attempt.path("evidenceVersionIds").size()).isZero();
        totals(detail, stock.rice(), 10, 10, 6, 4, 0, 2);
        totals(detail, stock.water(), 4, 4, 2, 1, 0, 1);
        assertThat(read(volunteer, stock.caseId())).isEqualTo(detail);
        assertThat(sequences(stock.caseId())).isEqualTo(chainsBeforeRead);
        assertThat(count("audit_events", "aid_case_id", stock.caseId())).isEqualTo(auditBeforeRead);
    }

    @Test
    void refreshReturnsLiveTotalsEvenWhenAnOlderD02ResponseIsReplayed() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID firstId = record(stock, stock.evidenceIds(), 3, 1);
        UUID secondId = record(stock, stock.evidenceIds(), 2, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        String firstKey = newKey();
        JsonNode first = confirm(reviewer, firstId, "CONFIRMED", stock.evidenceIds(), firstKey);
        JsonNode initialRead = read(reviewer, stock.caseId());
        assertThat(initialRead.path("goodsTotals")).isEqualTo(first.path("goodsTotals"));
        assertThat(one(initialRead.path("deliveries"), "id", firstId).path("reviewStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(one(initialRead.path("deliveries"), "id", secondId).path("reviewStatus").asText()).isEqualTo("PENDING");
        confirm(reviewer, secondId, "CONFIRMED", stock.evidenceIds(), newKey());
        JsonNode replay = confirm(reviewer, firstId, "CONFIRMED", stock.evidenceIds(), firstKey);
        assertThat(replay).isEqualTo(first);
        JsonNode refreshed = read(reviewer, stock.caseId());
        totals(refreshed, stock.rice(), 10, 10, 6, 5, 5, 1);
        totals(refreshed, stock.water(), 4, 4, 2, 2, 2, 0);
        assertThat(refreshed.path("goodsTotals")).isNotEqualTo(replay.path("goodsTotals"));
    }

    @Test
    void reviewDisputeIsVisibleAndResolvedUnconfirmedDeliveryReturnsToPending() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, stock.evidenceIds(), 4, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        JsonNode decision = confirm(reviewer, deliveryId, "REQUIRES_REVIEW", stock.evidenceIds(), newKey());
        UUID disputeId = UUID.fromString(decision.path("disputeId").asText());
        JsonNode detail = read(reviewer, stock.caseId());
        assertThat(detail.path("disputes").size()).isEqualTo(1);
        JsonNode dispute = one(detail.path("disputes"), "id", disputeId);
        assertThat(dispute.path("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(dispute.path("status").asText()).isEqualTo("OPEN");
        assertThat(dispute.path("reason").asText()).isEqualTo("Synthetic evidence discrepancy");
        assertThat(dispute.path("createdAt").asText()).isNotBlank();
        assertThat(one(detail.path("deliveries"), "id", deliveryId).path("reviewStatus").asText()).isEqualTo("REQUIRES_REVIEW");
        totals(detail, stock.rice(), 10, 10, 6, 4, 0, 2);
        // Read-projection fixture only: this does not implement or test the S03 resolve endpoint.
        jdbc.update("""
                UPDATE disputes SET status = 'RESOLVED', resolved_by = ?, resolved_at = NOW(),
                    resolution_note = 'Synthetic uphold', updated_at = NOW() WHERE id = ?
                """, admin.id(), disputeId);
        JsonNode resolved = read(reviewer, stock.caseId());
        assertThat(one(resolved.path("deliveries"), "id", deliveryId).path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(one(resolved.path("disputes"), "id", disputeId).path("status").asText()).isEqualTo("RESOLVED");
        totals(resolved, stock.rice(), 10, 10, 6, 4, 0, 2);
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT decision FROM delivery_confirmations WHERE delivery_id = ?",
                String.class, deliveryId)).isEqualTo("REQUIRES_REVIEW");
    }

    @Test
    void voidExcludesOnlyTheInvalidDeliveryFromTotalsAndKeepsHistory() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID invalidId = record(stock, stock.evidenceIds(), 4, 1);
        UUID validId = record(stock, stock.evidenceIds(), 2, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        confirm(reviewer, invalidId, "CONFIRMED", stock.evidenceIds(), newKey());
        confirm(reviewer, validId, "CONFIRMED", stock.evidenceIds(), newKey());
        JsonNode before = read(reviewer, stock.caseId());
        var chainsBefore = sequences(stock.caseId());
        UUID disputeId = UUID.randomUUID();
        // Seed an already completed void resolution; no claim about S03 behavior or authorization.
        jdbc.update("""
                INSERT INTO disputes (id, aid_case_id, delivery_id, opened_by, status, reason,
                    resolved_by, resolved_at, resolution_note)
                VALUES (?, ?, ?, ?, 'RESOLVED', 'Synthetic invalid delivery', ?, NOW(), 'Void delivery')
                """, disputeId, stock.caseId(), invalidId, reviewer.id(), admin.id());
        jdbc.update("""
                INSERT INTO delivery_voids (delivery_id, aid_case_id, dispute_id, voided_by, reason)
                VALUES (?, ?, ?, ?, 'Synthetic invalid delivery')
                """, invalidId, stock.caseId(), disputeId, admin.id());
        JsonNode after = read(reviewer, stock.caseId());
        totals(after, stock.rice(), 10, 10, 6, 2, 2, 4);
        totals(after, stock.water(), 4, 4, 2, 1, 1, 1);
        assertThat(after.path("outstandingCommitments")).isEqualTo(before.path("outstandingCommitments"));
        assertThat(after.path("deliveries").size()).isEqualTo(2);
        JsonNode invalid = one(after.path("deliveries"), "id", invalidId);
        assertThat(invalid.path("reviewStatus").asText()).isEqualTo("VOIDED");
        assertThat(invalid.path("outcome").asText()).isEqualTo("DELIVERED");
        assertThat(invalid.path("items")).isEqualTo(one(before.path("deliveries"), "id", invalidId).path("items"));
        assertThat(invalid.path("evidenceVersionIds")).isEqualTo(one(before.path("deliveries"), "id", invalidId).path("evidenceVersionIds"));
        assertThat(one(after.path("deliveries"), "id", validId).path("reviewStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(count("delivery_confirmations", "delivery_id", invalidId)).isEqualTo(1);
        assertThat(sequences(stock.caseId())).isEqualTo(chainsBefore);
        // Public delivered projection must exclude the void too, not only the D02/C03 totals query.
        var publicResponse = new Browser().send("GET", "/api/campaigns/" + stock.campaignId(), null, null, false);
        status(publicResponse, 200);
        JsonNode publicItems = node(publicResponse).path("items");
        assertThat(one(publicItems, "id", stock.rice()).path("deliveredQty").asLong()).isEqualTo(2);
        assertThat(one(publicItems, "id", stock.water()).path("deliveredQty").asLong()).isEqualTo(1);
    }

    @Test
    void caseLevelDisputeKeepsNullDeliveryIdWithoutRelabelingEveryDelivery() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, stock.evidenceIds(), 1, 1);
        UUID disputeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO disputes (id, aid_case_id, opened_by, status, reason)
                VALUES (?, ?, ?, 'OPEN', 'Synthetic case-level dispute')
                """, disputeId, stock.caseId(), verifier.id());
        JsonNode detail = read(volunteer, stock.caseId());
        JsonNode dispute = one(detail.path("disputes"), "id", disputeId);
        assertThat(dispute.has("deliveryId")).isTrue();
        assertThat(dispute.path("deliveryId").isNull()).isTrue();
        assertThat(one(detail.path("deliveries"), "id", deliveryId).path("reviewStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void privateReadRejectsOtherOrganizationsPlatformAdminAndRevokedMembership() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        UUID deliveryId = record(stock, stock.evidenceIds(), 1, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        read(reviewer, stock.caseId());
        for (Actor denied : List.of(other, admin, donor(true))) {
            var response = get(denied, stock.caseId());
            problem(response, 404, "RESOURCE_NOT_FOUND");
            assertThat(response.body()).doesNotContain(deliveryId.toString(), stock.evidenceIds().getFirst().toString());
        }
        problem(new Browser().send("GET", route(stock.caseId()), null, null, false), 401, "UNAUTHENTICATED");
        jdbc.update("UPDATE organization_memberships SET status = 'DISABLED' WHERE id = ?", reviewer.membership());
        problem(get(reviewer, stock.caseId()), 404, "RESOURCE_NOT_FOUND");
        // A second case in the SAME organization must not leak its deliveries into the first case.
        Stock second = stockedCase(orgAdmin);
        UUID secondDelivery = record(second, second.evidenceIds(), 2, 1);
        JsonNode firstDetail = read(volunteer, stock.caseId());
        assertThat(firstDetail.path("deliveries").size()).isEqualTo(1);
        assertThat(firstDetail.toString()).doesNotContain(secondDelivery.toString(), second.rice().toString());
    }

    private JsonNode read(Actor actor, UUID caseId) throws Exception {
        var response = get(actor, caseId);
        status(response, 200);
        assertThat(response.body()).doesNotContain("storageKey", "payload_private", "deliveryRef", "passwordHash");
        return node(response);
    }

    private HttpResponse<String> get(Actor actor, UUID caseId) throws Exception {
        return actor.browser().send("GET", route(caseId), null, null, false);
    }

    private static String route(UUID caseId) {
        return "/api/org/cases/" + caseId;
    }

    private UUID record(Stock stock, List<UUID> evidenceIds, int rice, int water) throws Exception {
        var response = deliver(volunteer, stock.caseId(), delivered(evidenceIds, stock.rice(), rice, stock.water(), water), newKey());
        status(response, 201);
        return id(node(response));
    }

    private JsonNode confirm(Actor actor, UUID deliveryId, String decision, List<UUID> evidenceIds, String key) throws Exception {
        var response = actor.browser().send("POST", "/api/deliveries/" + deliveryId + "/confirm",
                Map.of("decision", decision, "reviewedEvidenceVersionIds", evidenceIds,
                        "reason", "Synthetic evidence discrepancy"), key, true);
        status(response, 200);
        return node(response);
    }

    private static UUID id(JsonNode response) {
        return UUID.fromString(response.path("id").asText());
    }

    private JsonNode one(JsonNode array, String field, UUID id) {
        assertThat(array.isArray()).isTrue();
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode entry : array) {
            if (id.toString().equals(entry.path(field).asText())) matches.add(entry);
        }
        assertThat(matches).as("exactly one %s=%s", field, id).hasSize(1);
        return matches.getFirst();
    }

    private void totals(JsonNode detail, UUID item, long target, long pledged, long received,
                        long delivered, long confirmed, long available) {
        JsonNode total = one(detail.path("goodsTotals"), "campaignItemId", item);
        assertThat(total.path("itemName").asText()).isNotBlank();
        assertThat(total.path("unit").asText()).isNotBlank();
        assertThat(total.path("targetQty").asLong()).isEqualTo(target);
        assertThat(total.path("pledgedQty").asLong()).isEqualTo(pledged);
        assertThat(total.path("receivedQty").asLong()).isEqualTo(received);
        assertThat(total.path("deliveredQty").asLong()).isEqualTo(delivered);
        assertThat(total.path("confirmedQty").asLong()).isEqualTo(confirmed);
        assertThat(total.path("availableToDeliverQty").asLong()).isEqualTo(available);
    }
}
