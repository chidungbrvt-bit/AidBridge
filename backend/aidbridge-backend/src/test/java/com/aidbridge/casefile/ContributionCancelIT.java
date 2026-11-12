package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ContributionCancelIT extends AbstractCampaignHttpIT {

    // ---------------------------------------------------------------- G02 history

    @Test
    void historyListsOnlyTheCallersPledgesNewestFirstWithLiveChainStatus() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaign1 = publishedCampaign(orgAdmin), campaign2 = publishedCampaign(orgAdmin);
        List<UUID> items1 = campaignItemIds(campaign1), items2 = campaignItemIds(campaign2);

        JsonNode older = created(pledge(donorA, campaign1, body(items1.get(0), 1), newKey()));
        JsonNode newer = created(pledge(donorA, campaign2, body(items2.get(0), 2, items2.get(1), 1), newKey()));
        JsonNode othersPledge = created(pledge(donorB, campaign1, body(items1.get(1), 1), newKey()));

        var history = history(donorA, 0, 20);
        status(history, 200);
        JsonNode page = node(history);
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(ids(page)).containsExactly(id(newer), id(older));
        // A fresh pledge reads back exactly as G01 returned it, including createdAt to the microsecond.
        assertThat(page.path("items").get(0)).isEqualTo(newer);
        assertThat(history.body()).doesNotContain(id(othersPledge).toString(), donorB.id().toString(), "@example.com");

        // History reports the live job state, unlike the G01 replay snapshot.
        jdbc.update("UPDATE blockchain_jobs SET status = 'CONFIRMED' WHERE audit_event_id = ?",
                UUID.fromString(older.path("auditEventId").asText()));
        JsonNode refreshed = node(history(donorA, 0, 20));
        assertThat(refreshed.path("items").get(0).path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(refreshed.path("items").get(1).path("blockchainStatus").asText()).isEqualTo("CONFIRMED_ON_CHAIN");

        assertThat(ids(node(history(donorB, 0, 20)))).containsExactly(id(othersPledge));
    }

    @Test
    void historyPagingIsDeterministicValidatedAndDonorOnly() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        created(pledge(donor, campaignId, body(items.get(0), 1), newKey()));
        created(pledge(donor, campaignId, body(items.get(0), 1), newKey()));
        created(pledge(donor, campaignId, body(items.get(1), 1), newKey()));

        JsonNode first = node(history(donor, 0, 2));
        JsonNode second = node(history(donor, 1, 2));
        assertThat(first.path("totalElements").asLong()).isEqualTo(3);
        assertThat(ids(first)).hasSize(2);
        assertThat(ids(second)).hasSize(1).doesNotContainAnyElementsOf(ids(first));

        problem(history(donor, -1, 20), 400, "VALIDATION_ERROR");
        problem(history(donor, 0, 0), 400, "VALIDATION_ERROR");
        problem(history(donor, 0, 101), 400, "VALIDATION_ERROR");
        problem(history(donor(false), 0, 20), 403, "FORBIDDEN");
        problem(new Browser().send("GET", "/api/me/commitments", null, null, false), 401, "UNAUTHENTICATED");
    }

    // ---------------------------------------------------------------- G03 positive

    @Test
    void partialCancellationReleasesCapacityExactlyOnceAndQueuesAReduction() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);
        JsonNode pledged = created(pledge(donorA, campaignId, body(food, 2, hygiene, 2), newKey()));
        UUID contributionId = id(pledged);

        var response = cancel(donorA, contributionId, cancelBody("Partial stock only", lineId(pledged, food), 1), newKey());
        status(response, 200);
        JsonNode body = node(response);
        assertThat(body.path("status").asText()).isEqualTo("PLEDGED");
        assertThat(lineSignatures(body)).containsExactly(food + "|2|1|0|1", hygiene + "|2|0|0|2");
        assertThat(body.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");

        UUID reductionEvent = UUID.fromString(body.path("auditEventId").asText());
        assertThat(reductionEvent.toString()).isNotEqualTo(pledged.path("auditEventId").asText());
        assertThat(jdbc.queryForMap("""
                SELECT event_type, entity_type, entity_id, actor_user_id, event_key <> entity_id AS fresh_key
                FROM audit_events WHERE id = ?
                """, reductionEvent))
                .containsEntry("event_type", "COMMITMENT_REDUCED")
                .containsEntry("entity_type", "CONTRIBUTION")
                .containsEntry("entity_id", contributionId)
                .containsEntry("actor_user_id", donorA.id())
                .containsEntry("fresh_key", true);
        JsonNode payload = json.readTree(jdbc.queryForObject(
                "SELECT payload_private::text FROM audit_events WHERE id = ?", String.class, reductionEvent));
        assertThat(payload.path("reason").asText()).isEqualTo("Partial stock only");
        assertThat(payload.path("lines").size()).isEqualTo(1);
        assertThat(sequences(caseOf(campaignId)))
                .containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2", "COMMITMENT_REDUCED:3");

        // Exactly one unit was released (food target 2: 2 pledged - 1 cancelled).
        status(pledge(donorB, campaignId, body(food, 1), newKey()), 201);
        problem(pledge(donorB, campaignId, body(food, 1), newKey()), 409, "QUANTITY_EXCEEDED");
    }

    @Test
    void cancellingEverythingWithoutReceiptsMarksTheContributionCancelled() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);
        JsonNode pledged = created(pledge(donorA, campaignId, body(food, 2, hygiene, 1), newKey()));
        UUID contributionId = id(pledged);

        var response = cancel(donorA, contributionId,
                cancelBody("Plans changed", lineId(pledged, food), 2, lineId(pledged, hygiene), 1), newKey());
        status(response, 200);
        assertThat(node(response).path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM contributions WHERE id = ?", String.class, contributionId))
                .isEqualTo("CANCELLED");

        status(pledge(donorB, campaignId, body(food, 2), newKey()), 201);
        problem(cancel(donorA, contributionId, cancelBody("Again", lineId(pledged, food), 1), newKey()),
                409, "QUANTITY_EXCEEDED");
    }

    @Test
    void receivedPortionCannotBeCancelledAndCancellingTheRemainderSettlesAsReceived() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);
        JsonNode pledged = created(pledge(donor, campaignId, body(food, 2, hygiene, 2), newKey()));
        UUID contributionId = id(pledged), foodLine = lineId(pledged, food), hygieneLine = lineId(pledged, hygiene);
        seedReceipt(contributionId, foodLine, 1, orgAdmin.id()); // B08 owns receipts; seeded to test the B07 rule

        problem(cancel(donor, contributionId, cancelBody("Too much", foodLine, 2), newKey()), 409, "QUANTITY_EXCEEDED");
        assertThat(cancelledQuantity(foodLine)).isZero();

        JsonNode afterFood = node(cancel(donor, contributionId, cancelBody("Remainder", foodLine, 1), newKey()));
        assertThat(lineSignatures(afterFood)).containsExactly(food + "|2|1|1|0", hygiene + "|2|0|0|2");
        assertThat(afterFood.path("status").asText()).isEqualTo("PLEDGED");

        JsonNode afterHygiene = node(cancel(donor, contributionId, cancelBody("Remainder", hygieneLine, 2), newKey()));
        assertThat(afterHygiene.path("status").asText()).isEqualTo("RECEIVED");
    }

    // ---------------------------------------------------------------- G03 negative

    @Test
    void multiLineCancellationIsAtomic() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        JsonNode pledged = created(pledge(donor, campaignId, body(items.get(0), 2, items.get(1), 2), newKey()));
        UUID foodLine = lineId(pledged, items.get(0)), hygieneLine = lineId(pledged, items.get(1));

        problem(cancel(donor, id(pledged), cancelBody("Mixed", foodLine, 1, hygieneLine, 3), newKey()),
                409, "QUANTITY_EXCEEDED");

        assertThat(cancelledQuantity(foodLine)).isZero();
        assertThat(cancelledQuantity(hygieneLine)).isZero();
        assertThat(sequences(caseOf(campaignId))).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2");
    }

    @Test
    void malformedCancellationsAreRejectedBeforeAnyChange() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID otherCampaign = publishedCampaign(orgAdmin);
        JsonNode pledged = created(pledge(donor, campaignId, body(campaignItemIds(campaignId).getFirst(), 2), newKey()));
        JsonNode otherPledge = created(pledge(donor, otherCampaign, body(campaignItemIds(otherCampaign).getFirst(), 1), newKey()));
        UUID contributionId = id(pledged);
        UUID line = UUID.fromString(pledged.path("items").get(0).path("id").asText());
        UUID foreignLine = UUID.fromString(otherPledge.path("items").get(0).path("id").asText());

        for (Integer quantity : new Integer[] {0, -1, null}) {
            problem(cancel(donor, contributionId, cancelBody("Reason", line, quantity), newKey()), 400, "VALIDATION_ERROR");
        }
        problem(cancel(donor, contributionId, cancelBody("Reason", line, 1, line, 1), newKey()), 400, "VALIDATION_ERROR");
        for (String reason : new String[] {null, "   ", "R".repeat(501), "<b>now</b>", "see https://example.com"}) {
            problem(cancel(donor, contributionId, cancelBody(reason, line, 1), newKey()), 400, "VALIDATION_ERROR");
        }
        problem(cancel(donor, contributionId, cancelBody("Reason", foreignLine, 1), newKey()), 400, "VALIDATION_ERROR");
        problem(cancel(donor, contributionId, cancelBody("Reason", UUID.randomUUID(), 1), newKey()), 400, "VALIDATION_ERROR");
        problem(cancel(donor, contributionId, "{not json", newKey()), 400, "VALIDATION_ERROR");

        assertThat(cancelledQuantity(line)).isZero();
        assertThat(cancelledQuantity(foreignLine)).isZero();
    }

    @Test
    void onlyTheOwningEnabledDonorMayCancel() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor owner = donor(true), stranger = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        JsonNode pledged = created(pledge(owner, campaignId, body(campaignItemIds(campaignId).getFirst(), 2), newKey()));
        UUID contributionId = id(pledged);
        UUID line = UUID.fromString(pledged.path("items").get(0).path("id").asText());
        var request = cancelBody("Reason", line, 1);
        String path = "/api/commitments/" + contributionId + "/cancellations";

        problem(cancel(stranger, contributionId, request, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(cancel(owner, UUID.randomUUID(), request, newKey()), 404, "RESOURCE_NOT_FOUND");
        Browser anonymous = new Browser();
        anonymous.csrf();
        problem(anonymous.send("POST", path, request, newKey(), true), 401, "UNAUTHENTICATED");
        problem(owner.browser().send("POST", path, request, newKey(), false), 403, "CSRF_INVALID");
        jdbc.update("UPDATE users SET donor_enabled = FALSE WHERE id = ?", owner.id());
        problem(cancel(owner, contributionId, request, newKey()), 403, "FORBIDDEN");

        assertThat(cancelledQuantity(line)).isZero();
    }

    @Test
    void closedCampaignsAndOpenDisputesBlockCancellation() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID closed = publishedCampaign(orgAdmin), disputed = publishedCampaign(orgAdmin);
        JsonNode inClosed = created(pledge(donor, closed, body(campaignItemIds(closed).getFirst(), 1), newKey()));
        JsonNode inDisputed = created(pledge(donor, disputed, body(campaignItemIds(disputed).getFirst(), 1), newKey()));

        jdbc.update("UPDATE campaigns SET status = 'CLOSED', closed_at = now() WHERE id = ?", closed);
        problem(cancel(donor, id(inClosed), cancelBody("Reason",
                UUID.fromString(inClosed.path("items").get(0).path("id").asText()), 1), newKey()), 409, "INVALID_STATE");

        jdbc.update("""
                INSERT INTO disputes (aid_case_id, opened_by, status, reason)
                SELECT aid_case_id, ?, 'OPEN', 'Synthetic dispute' FROM campaigns WHERE id = ?
                """, orgAdmin.id(), disputed);
        problem(cancel(donor, id(inDisputed), cancelBody("Reason",
                UUID.fromString(inDisputed.path("items").get(0).path("id").asText()), 1), newKey()), 409, "DISPUTE_OPEN");
    }

    @Test
    void replayDoesNotCancelTwiceAndTheKeyIsBoundToThePayload() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        JsonNode pledged = created(pledge(donor, campaignId, body(campaignItemIds(campaignId).getFirst(), 2), newKey()));
        UUID contributionId = id(pledged);
        UUID line = UUID.fromString(pledged.path("items").get(0).path("id").asText());
        String key = newKey();

        var first = cancel(donor, contributionId, cancelBody("Reason", line, 1), key);
        status(first, 200);
        var replay = cancel(donor, contributionId, cancelBody("Reason", line, 1), key);
        status(replay, 200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(node(replay)).isEqualTo(node(first));
        assertThat(cancelledQuantity(line)).isEqualTo(1);
        assertThat(reductionCount(contributionId)).isEqualTo(1);

        problem(cancel(donor, contributionId, cancelBody("Reason", line, 2), key), 409, "IDEMPOTENCY_CONFLICT");
        problem(cancel(donor, contributionId, cancelBody("Reason", line, 1), null), 400, "VALIDATION_ERROR");
        assertThat(cancelledQuantity(line)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> pledge(Actor actor, UUID campaignId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments", body, key, true);
    }

    private HttpResponse<String> cancel(Actor actor, UUID contributionId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/commitments/" + contributionId + "/cancellations", body, key, true);
    }

    private HttpResponse<String> history(Actor actor, int page, int size) throws Exception {
        return actor.browser().send("GET", "/api/me/commitments?page=" + page + "&size=" + size, null, null, false);
    }

    private JsonNode created(HttpResponse<String> response) {
        status(response, 201);
        return node(response);
    }

    private static Map<String, Object> body(Object... pairs) {
        return lines("campaignItemId", pairs);
    }

    private static Map<String, Object> cancelBody(String reason, Object... pairs) {
        Map<String, Object> body = lines("contributionItemId", pairs);
        body.put("reason", reason);
        return body;
    }

    private static Map<String, Object> lines(String idField, Object... pairs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Map<String, Object> line = new HashMap<>();
            line.put(idField, pairs[i]);
            line.put("quantity", pairs[i + 1]);
            items.add(line);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        return body;
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
    }

    private static List<UUID> ids(JsonNode page) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : page.path("items")) {
            ids.add(id(item));
        }
        return ids;
    }

    private static UUID lineId(JsonNode commitment, UUID campaignItemId) {
        for (JsonNode line : commitment.path("items")) {
            if (line.path("campaignItemId").asText().equals(campaignItemId.toString())) {
                return id(line);
            }
        }
        throw new AssertionError("No line for campaign item " + campaignItemId);
    }

    /** campaignItemId|pledged|cancelled|received|outstanding, in response order. */
    private static List<String> lineSignatures(JsonNode commitment) {
        List<String> result = new ArrayList<>();
        for (JsonNode line : commitment.path("items")) {
            result.add(String.join("|", line.path("campaignItemId").asText(),
                    line.path("pledgedQty").asText(), line.path("cancelledQty").asText(),
                    line.path("receivedQty").asText(), line.path("outstandingQty").asText()));
        }
        return result;
    }

    private int cancelledQuantity(UUID contributionItemId) {
        return jdbc.queryForObject("SELECT cancelled_quantity FROM contribution_items WHERE id = ?",
                Integer.class, contributionItemId);
    }

    private long reductionCount(UUID contributionId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE entity_id = ? AND event_type = 'COMMITMENT_REDUCED'
                """, Long.class, contributionId);
    }

    private UUID caseOf(UUID campaignId) {
        return jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
    }

    private List<String> sequences(UUID caseId) {
        return jdbc.queryForList("""
                SELECT a.event_type || ':' || j.chain_sequence
                FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE j.aid_case_id = ?
                ORDER BY j.chain_sequence
                """, String.class, caseId);
    }

    private void seedReceipt(UUID contributionId, UUID contributionItemId, int quantity, UUID recorderId) {
        UUID receiptId = UUID.randomUUID();
        jdbc.update("INSERT INTO goods_receipts (id, contribution_id, received_by, idempotency_key) VALUES (?, ?, ?, ?)",
                receiptId, contributionId, recorderId, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO goods_receipt_items (id, goods_receipt_id, contribution_item_id, received_quantity)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), receiptId, contributionItemId, quantity);
    }
}
