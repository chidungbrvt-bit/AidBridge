package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ReceiptApiIT extends AbstractCampaignHttpIT {
    @Test
    void partialReceiptReplaysOnceAndRefreshesStaffDonorAndPublicTotals() throws Exception {
        var setup = setup();
        String key = newKey();
        var staffBefore = setup.orgAdmin().browser().send("GET", staffPath(setup), null, null, false);
        status(staffBefore, 200);
        assertThat(node(staffBefore).path("id").asText()).isEqualTo(setup.caseId().toString());
        var outstanding = node(staffBefore).path("outstandingCommitments").get(0);
        var firstLine = outstanding.path("items").get(0);
        var secondLine = outstanding.path("items").get(1);
        assertThat(outstanding.path("contributionId").asText()).isEqualTo(setup.contributionId().toString());
        assertThat(firstLine.path("contributionItemId").asText()).isEqualTo(setup.firstLine().toString());
        assertThat(staffBefore.body()).doesNotContain("donorUserId", "donorEmail", setup.donor().id().toString());
        var body = Map.of("receivedAt", Instant.now().toString(), "items", List.of(
                line(UUID.fromString(firstLine.path("contributionItemId").asText()), 1),
                line(UUID.fromString(secondLine.path("contributionItemId").asText()), 1)));

        var first = receive(setup.orgAdmin(), setup, body, key);
        status(first, 201);
        JsonNode receipt = node(first);
        UUID receiptId = id(receipt);
        UUID auditEventId = UUID.fromString(receipt.path("auditEventId").asText());
        assertThat(receipt.size()).isEqualTo(8);
        assertThat(receipt.path("contributionId").asText()).isEqualTo(setup.contributionId().toString());
        assertThat(receipt.path("recorderId").asText()).isEqualTo(setup.orgAdmin().id().toString());
        assertThat(receipt.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(receipt.path("items")).isEqualTo(json.readTree(json.writeValueAsString(body.get("items"))));
        assertThat(first.headers().firstValue("Location")).contains("/api/receipts/" + receiptId);

        var replay = receive(setup.orgAdmin(), setup, body, key);
        status(replay, 201);
        assertThat(node(replay)).isEqualTo(receipt);
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(receive(setup.orgAdmin(), setup, request(line(setup.firstLine(), 2)), key), 409,
                "IDEMPOTENCY_CONFLICT");

        assertThat(count("goods_receipts", "contribution_id", setup.contributionId())).isEqualTo(1);
        assertThat(count("goods_receipt_items", "goods_receipt_id", receiptId)).isEqualTo(2);
        assertThat(count("audit_events", "entity_id", receiptId)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT a.event_type, a.event_key, j.status, j.chain_sequence
                FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ? AND j.aid_case_id = ?
                """, auditEventId, setup.caseId())).containsEntry("event_type", "GOODS_RECEIVED")
                .containsEntry("event_key", receiptId).containsEntry("status", "PENDING")
                .containsEntry("chain_sequence", 3L);
        assertThat(count("deliveries", "aid_case_id", setup.caseId())).isZero();

        var history = setup.donor().browser().send("GET", "/api/me/commitments", null, null, false);
        status(history, 200);
        var commitment = node(history).path("items").get(0);
        assertThat(commitment.path("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(commitment.path("items").get(0).path("receivedQty").asInt()).isEqualTo(1);
        assertThat(commitment.path("items").get(0).path("outstandingQty").asInt()).isEqualTo(1);

        var staffAfter = volunteer.browser().send("GET", staffPath(setup), null, null, false);
        status(staffAfter, 200);
        assertThat(node(staffAfter).path("outstandingCommitments").get(0).path("items").get(0)
                .path("outstandingQty").asInt()).isEqualTo(1);
        var campaign = setup.donor().browser().send("GET", "/api/campaigns/" + setup.campaignId(), null, null, false);
        status(campaign, 200);
        for (var item : node(campaign).path("items")) {
            assertThat(item.path("receivedQty").asInt()).isEqualTo(1);
            assertThat(item.path("deliveredQty").asInt()).isZero();
            assertThat(item.path("confirmedQty").asInt()).isZero();
        }

        problem(cancel(setup, 2), 409, "QUANTITY_EXCEEDED");
        status(cancel(setup, 1), 200);
        status(receive(setup.orgAdmin(), setup, request(line(setup.secondLine(), 1)), newKey()), 201);
        assertThat(jdbc.queryForObject("SELECT status FROM contributions WHERE id = ?",
                String.class, setup.contributionId())).isEqualTo("RECEIVED");
        var staffComplete = volunteer.browser().send("GET", staffPath(setup), null, null, false);
        status(staffComplete, 200);
        assertThat(node(staffComplete).path("outstandingCommitments").size()).isZero();
    }

    @Test
    void quantityOrForeignLineFailureLeavesTheWholeReceiptUnwritten() throws Exception {
        var setup = setup();
        var foreign = setup();
        problem(receive(setup.orgAdmin(), setup,
                request(line(setup.firstLine(), 1), line(setup.secondLine(), 3)), newKey()),
                409, "QUANTITY_EXCEEDED");
        problem(receive(setup.orgAdmin(), setup,
                request(line(setup.firstLine(), 1), line(foreign.firstLine(), 1)), newKey()),
                409, "INVALID_STATE");
        assertThat(count("goods_receipts", "contribution_id", setup.contributionId())).isZero();
        assertThat(count("goods_receipt_items", "contribution_id", setup.contributionId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'GOODS_RECEIVED'
                """, Long.class, setup.caseId())).isZero();
        assertThat(count("blockchain_jobs", "aid_case_id", setup.caseId())).isEqualTo(2); // verification + pledge
        assertThat(jdbc.queryForObject("SELECT status FROM contributions WHERE id = ?",
                String.class, setup.contributionId())).isEqualTo("PLEDGED");
    }

    @Test
    void sessionScopeAndRoleAreCheckedBeforeReplay() throws Exception {
        var setup = setup();
        var body = request(line(setup.firstLine(), 1));
        problem(receive(volunteer, setup, body, newKey()), 403, "FORBIDDEN");
        var foreignAdmin = actor(ORG_B, "ORG_ADMIN", false);
        problem(receive(foreignAdmin, setup, body, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(foreignAdmin.browser().send("GET", staffPath(setup), null, null, false), 404, "RESOURCE_NOT_FOUND");
        String key = newKey();
        status(receive(setup.orgAdmin(), setup, body, key), 201);
        jdbc.update("UPDATE membership_roles SET role = 'VOLUNTEER' WHERE membership_id = ?",
                setup.orgAdmin().membership());
        problem(receive(setup.orgAdmin(), setup, body, key), 403, "FORBIDDEN");
        assertThat(count("goods_receipts", "contribution_id", setup.contributionId())).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateLinesAndTimestampsOutsideTheCampaignWindow() throws Exception {
        var setup = setup();
        problem(receive(setup.orgAdmin(), setup, request(line(setup.firstLine(), 1), line(setup.firstLine(), 1)),
                newKey()), 400, "VALIDATION_ERROR");
        for (var time : List.of(Instant.EPOCH, Instant.now().plusSeconds(600))) {
            problem(receive(setup.orgAdmin(), setup,
                    Map.of("receivedAt", time.toString(), "items", List.of(line(setup.firstLine(), 1))),
                    newKey()), 400, "VALIDATION_ERROR");
        }
        assertThat(count("goods_receipts", "contribution_id", setup.contributionId())).isZero();
    }

    @Test
    void receiptAndCancellationCompeteForTheSameOutstandingUnits() throws Exception {
        var setup = setup();
        var body = request(line(setup.firstLine(), 2));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var receipt = executor.submit(() -> {
                start.await();
                return receive(setup.orgAdmin(), setup, body, newKey());
            });
            var cancellation = executor.submit(() -> {
                start.await();
                return cancel(setup, 2);
            });
            start.countDown();
            var received = receipt.get(35, TimeUnit.SECONDS);
            var cancelled = cancellation.get(35, TimeUnit.SECONDS);
            if (received.statusCode() == 201) {
                status(received, 201);
                problem(cancelled, 409, "QUANTITY_EXCEEDED");
            } else {
                problem(received, 409, "QUANTITY_EXCEEDED");
                status(cancelled, 200);
            }
        }
        assertThat(jdbc.queryForObject("""
                SELECT ci.cancelled_quantity + COALESCE(SUM(gri.received_quantity), 0)
                FROM contribution_items ci LEFT JOIN goods_receipt_items gri ON gri.contribution_item_id = ci.id
                WHERE ci.id = ? GROUP BY ci.id
                """, Long.class, setup.firstLine())).isEqualTo(2L);
        assertThat(count("blockchain_jobs", "aid_case_id", setup.caseId())).isEqualTo(3);
    }

    private Setup setup() throws Exception {
        var orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        var donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        var items = campaignItemIds(campaignId);
        var pledged = donor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments",
                Map.of("items", items.stream().map(item -> Map.of("campaignItemId", item, "quantity", 2)).toList()),
                newKey(), true);
        status(pledged, 201);
        var contribution = node(pledged);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        return new Setup(orgAdmin, donor, caseId, campaignId, id(contribution),
                id(contribution.path("items").get(0)), id(contribution.path("items").get(1)));
    }

    private record Setup(Actor orgAdmin, Actor donor, UUID caseId, UUID campaignId, UUID contributionId,
                         UUID firstLine, UUID secondLine) {
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
    }

    private static String staffPath(Setup setup) {
        return "/api/org/cases/" + setup.caseId();
    }

    private static Map<String, Object> line(UUID id, int quantity) {
        return Map.of("contributionItemId", id.toString(), "quantity", quantity);
    }

    @SafeVarargs
    private static Map<String, Object> request(Map<String, Object>... lines) {
        return Map.of("receivedAt", Instant.now().toString(), "items", List.of(lines));
    }

    private HttpResponse<String> receive(Actor actor, Setup setup, Object body, String key)
            throws Exception {
        return actor.browser().send("POST", "/api/commitments/" + setup.contributionId() + "/receipts", body, key, true);
    }

    private HttpResponse<String> cancel(Setup setup, int quantity) throws Exception {
        return setup.donor().browser().send("POST", "/api/commitments/" + setup.contributionId() + "/cancellations",
                Map.of("items", List.of(line(setup.firstLine(), quantity)), "reason", "Unable to supply"),
                newKey(), true);
    }
}
