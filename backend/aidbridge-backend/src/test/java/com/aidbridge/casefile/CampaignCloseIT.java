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

/** P05 through HTTP with the isolated PostgreSQL test schema. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class CampaignCloseIT extends AbstractDeliveryHttpIT {

    @Test
    void closesCaseWithAllTargetsConfirmedAndReplaysTheOriginalResponseOnce() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = fulfilledCase(orgAdmin, actor(ORG_A, "DELIVERY_CONFIRMER", false));
        var sequencesBefore = sequences(stock.caseId());
        String key = newKey();

        // Simulate reopening the staff screen after the independent reviewer confirms the last item.
        var detail = orgAdmin.browser().send("GET", "/api/org/cases/" + stock.caseId(), null, null, false);
        status(detail, 200);
        assertThat(node(detail).hasNonNull("campaignId")).isFalse();
        var reviewCampaign = node(detail).path("reviewCampaign");
        assertThat(reviewCampaign.path("status").asText()).isEqualTo("FULFILLED");
        assertThat(reviewCampaign.path("id").asText()).isEqualTo(stock.campaignId().toString());

        var closed = orgAdmin.browser().send("POST",
                "/api/campaigns/" + reviewCampaign.path("id").asText() + "/close", Map.of(), key, true);
        status(closed, 200);
        var response = node(closed);
        assertThat(response.path("id").asText()).isEqualTo(stock.campaignId().toString());
        assertThat(response.path("status").asText()).isEqualTo("CLOSED");
        assertThat(confirmedQty(response, stock.rice())).isEqualTo(10);
        assertThat(confirmedQty(response, stock.water())).isEqualTo(4);
        assertThat(closed.headers().firstValue("Location")).isEmpty();
        assertClosed(stock);
        assertThat(jdbc.queryForMap("""
                SELECT actor_user_id, entity_type, entity_id
                FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_COMPLETED'
                """, stock.caseId())).containsEntry("actor_user_id", orgAdmin.id())
                .containsEntry("entity_type", "AID_CASE").containsEntry("entity_id", stock.caseId());

        var replay = close(orgAdmin, stock, key);
        status(replay, 200);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(close(orgAdmin, stock, newKey()), 409, "INVALID_STATE");
        jdbc.update("UPDATE organization_memberships SET status = 'DISABLED' WHERE id = ?", orgAdmin.membership());
        problem(close(orgAdmin, stock, key), 404, "RESOURCE_NOT_FOUND");
        assertThat(eventCount(stock, "CASE_COMPLETED")).isEqualTo(1);
        assertThat(sequences(stock.caseId())).isEqualTo(sequencesBefore);
    }

    @Test
    void rejectsPledgedTotalsAndRechecksEveryConfirmedTargetEvenIfCampaignSaysFulfilled() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Stock stock = fullyReceivedCase(orgAdmin);
        problem(close(orgAdmin, stock, newKey()), 409, "INVALID_STATE");

        recordAndConfirm(reviewer, stock, 10, 3);
        var publicRead = new Browser().send("GET", "/api/campaigns/" + stock.campaignId(), null, null, false);
        status(publicRead, 200);
        assertThat(confirmedQty(node(publicRead), stock.rice())).isEqualTo(10);
        assertThat(confirmedQty(node(publicRead), stock.water())).isEqualTo(3);
        assertThat(node(publicRead).path("status").asText()).isEqualTo("PUBLISHED");

        // Simulate a stale status to exercise the quantity guard independently of the status guard.
        jdbc.update("UPDATE campaigns SET status = 'FULFILLED' WHERE id = ?", stock.campaignId());
        problem(close(orgAdmin, stock, newKey()), 409, "INVALID_STATE");
        assertThat(caseStatus(stock)).isEqualTo("IN_PROGRESS");
        assertThat(campaignIsOpen(stock)).isTrue();
        assertThat(eventCount(stock, "CASE_COMPLETED")).isZero();
    }

    @Test
    void lastResolvedDisputeAllowsAnExplicitClose() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = fulfilledCase(orgAdmin, actor(ORG_A, "DELIVERY_CONFIRMER", false));
        var opened = openDispute(verifier, stock);
        status(opened, 201);
        UUID disputeId = UUID.fromString(node(opened).path("id").asText());
        String key = newKey();

        problem(close(orgAdmin, stock, key), 409, "DISPUTE_OPEN");
        assertThat(caseStatus(stock)).isEqualTo("IN_PROGRESS");
        assertThat(eventCount(stock, "CASE_COMPLETED")).isZero();

        status(admin.browser().send("POST", "/api/disputes/" + disputeId + "/resolve",
                Map.of("outcome", "UPHOLD", "reason", "The recorded facts were verified.",
                        "reviewEvidenceVersionIds", List.of()), newKey(), true), 200);
        assertThat(caseStatus(stock)).isEqualTo("IN_PROGRESS");
        assertThat(campaignIsOpen(stock)).isTrue();
        status(close(orgAdmin, stock, key), 200);
        assertClosed(stock);
        assertThat(eventCount(stock, "CASE_COMPLETED")).isEqualTo(1);
    }

    @Test
    void requiresOwningOrgAdminCsrfEmptyBodyAndIdempotencyKey() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor foreignAdmin = actor(ORG_B, "ORG_ADMIN", false);
        Stock stock = fulfilledCase(orgAdmin, actor(ORG_A, "DELIVERY_CONFIRMER", false));
        String route = route(stock);

        problem(close(verifier, stock, newKey()), 403, "FORBIDDEN");
        problem(close(foreignAdmin, stock, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(close(admin, stock, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(close(orgAdmin, stock, null), 400, "VALIDATION_ERROR");
        problem(orgAdmin.browser().send("POST", route, Map.of(), newKey(), false), 403, "CSRF_INVALID");
        problem(orgAdmin.browser().send("POST", route, Map.of("status", "CLOSED"), newKey(), true),
                400, "VALIDATION_ERROR");
        assertThat(caseStatus(stock)).isEqualTo("IN_PROGRESS");
        assertThat(campaignIsOpen(stock)).isTrue();
        assertThat(eventCount(stock, "CASE_COMPLETED")).isZero();
    }

    @Test
    void concurrentCloseAndDisputeAlwaysLeaveTheCaseOpenWithTheDispute() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        for (int round = 0; round < 3; round++) {
            Stock stock = fulfilledCase(orgAdmin, reviewer);
            String key = newKey();
            var responses = race(List.of(
                    () -> close(orgAdmin, stock, key),
                    () -> openDispute(verifier, stock)));

            var closeResult = responses.get(0);
            var disputeResult = responses.get(1);
            status(disputeResult, 201);
            boolean closedFirst = closeResult.statusCode() == 200;
            if (closedFirst) {
                status(closeResult, 200);
                assertThat(node(closeResult).path("status").asText()).isEqualTo("CLOSED");
                var replay = close(orgAdmin, stock, key);
                status(replay, 200);
                assertThat(node(replay)).isEqualTo(node(closeResult));
                assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
            } else {
                problem(closeResult, 409, "DISPUTE_OPEN");
            }

            assertThat(caseStatus(stock)).as("round %s case status", round).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, stock.campaignId()))
                    .isEqualTo("FULFILLED");
            assertThat(campaignIsOpen(stock)).isTrue();
            assertThat(jdbc.queryForList("SELECT status FROM disputes WHERE aid_case_id = ?", String.class, stock.caseId()))
                    .containsExactly("OPEN");
            assertThat(eventCount(stock, "DISPUTE_OPENED")).isEqualTo(1);
            assertThat(eventCount(stock, "CASE_COMPLETED")).isEqualTo(closedFirst ? 1 : 0);
        }
    }

    private Stock fulfilledCase(Actor orgAdmin, Actor reviewer) throws Exception {
        Stock stock = fullyReceivedCase(orgAdmin);
        recordAndConfirm(reviewer, stock, 10, 4);
        return stock;
    }

    private Stock fullyReceivedCase(Actor orgAdmin) throws Exception {
        Stock stock = stockedCase(orgAdmin);
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
        return stock;
    }

    private void recordAndConfirm(Actor reviewer, Stock stock, int rice, int water) throws Exception {
        var delivery = deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), rice, stock.water(), water), newKey());
        status(delivery, 201);
        String deliveryId = node(delivery).path("id").asText();
        status(reviewer.browser().send("POST", "/api/deliveries/" + deliveryId + "/confirm",
                Map.of("decision", "CONFIRMED", "reviewedEvidenceVersionIds", stock.evidenceIds()), newKey(), true), 200);
    }

    private HttpResponse<String> close(Actor actor, Stock stock, String key) throws Exception {
        return actor.browser().send("POST", route(stock), Map.of(), key, true);
    }

    private HttpResponse<String> openDispute(Actor actor, Stock stock) throws Exception {
        return actor.browser().send("POST", "/api/cases/" + stock.caseId() + "/disputes",
                Map.of("reason", "The recorded facts need another review."), newKey(), true);
    }

    private void assertClosed(Stock stock) {
        assertThat(caseStatus(stock)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, stock.campaignId()))
                .isEqualTo("CLOSED");
        assertThat(campaignIsOpen(stock)).isFalse();
    }

    private String caseStatus(Stock stock) {
        return jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, stock.caseId());
    }

    private boolean campaignIsOpen(Stock stock) {
        return jdbc.queryForObject("SELECT closed_at IS NULL FROM campaigns WHERE id = ?", Boolean.class, stock.campaignId());
    }

    private long eventCount(Stock stock, String eventType) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = ?",
                Long.class, stock.caseId(), eventType);
    }

    private static int confirmedQty(JsonNode campaign, UUID itemId) {
        for (JsonNode item : campaign.path("items")) {
            if (itemId.toString().equals(item.path("id").asText())) {
                return item.path("confirmedQty").asInt(-1);
            }
        }
        throw new AssertionError("Missing item " + itemId);
    }

    private static String route(Stock stock) {
        return "/api/campaigns/" + stock.campaignId() + "/close";
    }
}
