package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ContributionPledgeIT extends AbstractCampaignHttpIT {

    // ---------------------------------------------------------------- BR04-P, BR11-P

    @Test
    void donorPledgesTwoLinesAndReplayReturnsTheOriginalResult() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);
        String key = newKey();
        // Request order differs from display order on purpose: the response must not echo request order.
        var request = body(hygiene, 1, food, 2);

        var created = pledge(donor, campaignId, request, key);
        status(created, 201);
        JsonNode first = node(created);
        UUID contributionId = UUID.fromString(first.path("id").asText());
        assertThat(created.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/api/commitments/" + contributionId));
        assertThat(created.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(first.path("campaignId").asText()).isEqualTo(campaignId.toString());
        assertThat(first.path("status").asText()).isEqualTo("PLEDGED");
        assertThat(first.path("createdAt").asText()).isNotBlank();
        assertThat(lineSignatures(first)).containsExactly(food + "|2|0|0|2", hygiene + "|1|0|0|1");
        assertThat(created.body()).doesNotContain(donor.id().toString(), "@example.com");

        assertThat(jdbc.queryForMap(
                "SELECT donor_user_id, organization_id, status FROM contributions WHERE id = ?", contributionId))
                .containsEntry("donor_user_id", donor.id())
                .containsEntry("organization_id", ORG_A)
                .containsEntry("status", "PLEDGED");
        assertThat(count("contribution_items", "contribution_id", contributionId)).isEqualTo(2);

        var replay = pledge(donor, campaignId, request, key);
        status(replay, 201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(replay.headers().firstValue("Location")).isEqualTo(created.headers().firstValue("Location"));
        assertThat(node(replay)).isEqualTo(first);
        assertThat(count("contributions", "donor_user_id", donor.id())).isEqualTo(1);
        assertThat(activePledged(food)).isEqualTo(2);
    }

    // ---------------------------------------------------------------- BR04-N

    @Test
    void lineAboveRemainingCapacityIsRejectedIncludingOtherDonorsPledges() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst(); // target 2

        problem(pledge(donorA, campaignId, body(food, 3), newKey()), 409, "QUANTITY_EXCEEDED");
        status(pledge(donorA, campaignId, body(food, 2), newKey()), 201);
        problem(pledge(donorB, campaignId, body(food, 1), newKey()), 409, "QUANTITY_EXCEEDED");

        assertThat(activePledged(food)).isEqualTo(2);
        assertThat(count("contributions", "donor_user_id", donorB.id())).isZero();
    }

    @Test
    void multiLinePledgeIsAtomicWhenOneLineOverflows() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);

        problem(pledge(donor, campaignId, body(food, 2, hygiene, 3), newKey()), 409, "QUANTITY_EXCEEDED");
        assertThat(count("contributions", "campaign_id", campaignId)).isZero();
        assertThat(count("contribution_items", "campaign_id", campaignId)).isZero();

        // Nothing was reserved: the valid line alone still has its full capacity.
        status(pledge(donor, campaignId, body(food, 2), newKey()), 201);
    }

    @Test
    void campaignMustBePublicPublishedAndFreeOfOpenDisputes() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);

        UUID draft = draftCampaign(orgAdmin, null);
        UUID draftItem = campaignItemIds(draft).getFirst();
        problem(pledge(donor, draft, body(draftItem, 1), newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(pledge(donor, UUID.randomUUID(), body(draftItem, 1), newKey()), 404, "RESOURCE_NOT_FOUND");

        UUID closed = publishedCampaign(orgAdmin);
        jdbc.update("UPDATE campaigns SET status = 'CLOSED', closed_at = now() WHERE id = ?", closed);
        problem(pledge(donor, closed, body(campaignItemIds(closed).getFirst(), 1), newKey()), 409, "INVALID_STATE");

        UUID fulfilled = publishedCampaign(orgAdmin);
        jdbc.update("UPDATE campaigns SET status = 'FULFILLED' WHERE id = ?", fulfilled);
        problem(pledge(donor, fulfilled, body(campaignItemIds(fulfilled).getFirst(), 1), newKey()),
                409, "INVALID_STATE");

        UUID disputed = publishedCampaign(orgAdmin);
        jdbc.update("""
                INSERT INTO disputes (aid_case_id, opened_by, status, reason)
                SELECT aid_case_id, ?, 'OPEN', 'Synthetic dispute' FROM campaigns WHERE id = ?
                """, orgAdmin.id(), disputed);
        problem(pledge(donor, disputed, body(campaignItemIds(disputed).getFirst(), 1), newKey()),
                409, "DISPUTE_OPEN");

        // Last on purpose: @BeforeEach in the base class restores ORG_A to APPROVED for the next test.
        UUID suspended = publishedCampaign(orgAdmin);
        jdbc.update("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?", ORG_A);
        problem(pledge(donor, suspended, body(campaignItemIds(suspended).getFirst(), 1), newKey()),
                404, "RESOURCE_NOT_FOUND");

        assertThat(count("contributions", "donor_user_id", donor.id())).isZero();
    }

    @Test
    void malformedLinesAreRejectedBeforeAnyWrite() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst();
        UUID itemOfAnotherCampaign = campaignItemIds(publishedCampaign(orgAdmin)).getFirst();

        problem(pledge(donor, campaignId, body(food, 1, food, 1), newKey()), 400, "VALIDATION_ERROR");
        problem(pledge(donor, campaignId, body(itemOfAnotherCampaign, 1), newKey()), 400, "VALIDATION_ERROR");
        problem(pledge(donor, campaignId, body(UUID.randomUUID(), 1), newKey()), 400, "VALIDATION_ERROR");
        for (Integer quantity : new Integer[] {0, -1, 1_000_001, null}) {
            problem(pledge(donor, campaignId, body(food, quantity), newKey()), 400, "VALIDATION_ERROR");
        }
        problem(pledge(donor, campaignId, Map.of("items", List.of()), newKey()), 400, "VALIDATION_ERROR");
        problem(pledge(donor, campaignId, Map.of(), newKey()), 400, "VALIDATION_ERROR");
        problem(pledge(donor, campaignId, "{not json", newKey()), 400, "VALIDATION_ERROR");

        List<Object> fiftyOne = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            fiftyOne.add(Map.of("campaignItemId", UUID.randomUUID(), "quantity", 1));
        }
        problem(pledge(donor, campaignId, Map.of("items", fiftyOne), newKey()), 400, "VALIDATION_ERROR");

        assertThat(count("contributions", "campaign_id", campaignId)).isZero();
    }

    // ---------------------------------------------------------------- authorization / security

    @Test
    void onlyAuthenticatedActiveDonorsWithAnExplicitGrantMayPledge() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin);
        String path = "/api/campaigns/" + campaignId + "/commitments";
        var request = body(campaignItemIds(campaignId).getFirst(), 1);

        problem(pledge(donor(false), campaignId, request, newKey()), 403, "FORBIDDEN");
        problem(pledge(admin, campaignId, request, newKey()), 403, "FORBIDDEN");    // no platform bypass
        problem(pledge(orgAdmin, campaignId, request, newKey()), 403, "FORBIDDEN"); // staff need the grant too

        // CsrfFilter runs before authentication: an anonymous client must hold a token to reach 401.
        Browser anonymous = new Browser();
        anonymous.csrf();
        problem(anonymous.send("POST", path, request, newKey(), true), 401, "UNAUTHENTICATED");

        Actor donor = donor(true);
        problem(donor.browser().send("POST", path, request, newKey(), false), 403, "CSRF_INVALID");

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", donor.id());
        problem(pledge(donor, campaignId, request, newKey()), 401, "INVALID_CREDENTIALS");

        assertThat(count("contributions", "campaign_id", campaignId)).isZero();
    }

    // ---------------------------------------------------------------- BR11-N

    @Test
    void idempotencyKeyIsRequiredBoundToPayloadAndReauthorizedOnReplay() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst();

        problem(pledge(donor, campaignId, body(food, 1), null), 400, "VALIDATION_ERROR");
        problem(pledge(donor, campaignId, body(food, 1), "too-short"), 400, "VALIDATION_ERROR");

        String key = newKey();
        status(pledge(donor, campaignId, body(food, 1), key), 201);
        problem(pledge(donor, campaignId, body(food, 2), key), 409, "IDEMPOTENCY_CONFLICT");
        assertThat(activePledged(food)).isEqualTo(1);

        // Authorization runs before replay: a revoked grant cannot read back the stored response.
        jdbc.update("UPDATE users SET donor_enabled = FALSE WHERE id = ?", donor.id());
        problem(pledge(donor, campaignId, body(food, 1), key), 403, "FORBIDDEN");
    }

    // ---------------------------------------------------------------- concurrency (real PostgreSQL)

    @Test
    void lastUnitRaceAdmitsExactlyTheTargetQuantity() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin, List.of(Map.of("name", "Rice", "unit", "BAG", "quantity", 10)));
        UUID rice = campaignItemIds(campaignId).getFirst();
        List<Actor> donors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            donors.add(donor(true));
        }

        List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
        for (Actor d : donors) {
            calls.add(() -> pledge(d, campaignId, body(rice, 1), newKey()));
        }
        var responses = race(calls);

        assertOutcome(responses, 10, 10);
        assertThat(activePledged(rice)).isEqualTo(10);
        assertThat(count("contributions", "campaign_id", campaignId)).isEqualTo(10);
    }

    @Test
    void twoSimultaneousOverlappingPledgesCommitOnlyOne() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin, List.of(Map.of("name", "Rice", "unit", "BAG", "quantity", 10)));
        UUID rice = campaignItemIds(campaignId).getFirst();
        Actor donorA = donor(true), donorB = donor(true);

        var responses = race(List.of(
                () -> pledge(donorA, campaignId, body(rice, 6), newKey()),
                () -> pledge(donorB, campaignId, body(rice, 6), newKey())));

        assertOutcome(responses, 1, 1);
        assertThat(activePledged(rice)).isEqualTo(6);
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> pledge(Actor actor, UUID campaignId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments", body, key, true);
    }

    /** Builds {items:[{campaignItemId, quantity}]} from (UUID, Integer) pairs; null quantities are allowed. */
    private static Map<String, Object> body(Object... pairs) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Map<String, Object> line = new HashMap<>();
            line.put("campaignItemId", pairs[i]);
            line.put("quantity", pairs[i + 1]);
            items.add(line);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        return body;
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

    private long activePledged(UUID campaignItemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(pledged_quantity - cancelled_quantity), 0)
                FROM contribution_items WHERE campaign_item_id = ?
                """, Long.class, campaignItemId);
    }

    /** Releases every call at the same instant; invariants are checked in the database afterwards. */
    private static List<HttpResponse<String>> race(List<Callable<HttpResponse<String>>> calls) throws Exception {
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

    /** Only 201 or 409 QUANTITY_EXCEEDED are acceptable; a deadlock (409 VERSION_CONFLICT) or 500 fails. */
    private void assertOutcome(List<HttpResponse<String>> responses, int created, int rejected) {
        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201, 409);
        assertThat(responses.stream().filter(r -> r.statusCode() == 201).count()).isEqualTo(created);
        assertThat(responses.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(rejected);
        responses.stream().filter(r -> r.statusCode() == 409)
                .forEach(r -> problem(r, 409, "QUANTITY_EXCEEDED"));
    }
}
