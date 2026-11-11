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

/**
 * Invariants under real contention. A failure here, even an occasional one, is a real bug until proven
 * otherwise: never retry until green and never loosen an assertion.
 */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ContributionConcurrencyIT extends AbstractCampaignHttpIT {

    private static final List<Map<String, Object>> RICE_10 =
            List.of(Map.of("name", "Rice", "unit", "BAG", "quantity", 10));

    @Test
    void cancellationRacingAPledgeNeverExceedsTheTarget() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        // Either order is legal; each round may take a different one. Only the invariant is asserted.
        for (int round = 0; round < 5; round++) {
            Actor holder = donor(true), newcomer = donor(true);
            UUID campaignId = publishedCampaign(orgAdmin, RICE_10);
            UUID rice = campaignItemIds(campaignId).getFirst();
            JsonNode full = created(pledge(holder, campaignId, body(rice, 10), newKey()));
            UUID contributionId = id(full);
            UUID line = id(full.path("items").get(0));

            var responses = race(List.of(
                    () -> cancel(holder, contributionId, cancelBody("Releasing four", line, 4), newKey()),
                    () -> pledge(newcomer, campaignId, body(rice, 4), newKey())));
            var cancelled = responses.get(0);
            var pledged = responses.get(1);

            status(cancelled, 200);
            assertThat(pledged.statusCode()).as("round %d", round).isIn(201, 409);
            if (pledged.statusCode() == 409) {
                problem(pledged, 409, "QUANTITY_EXCEEDED");
            }
            long expected = 6 + (pledged.statusCode() == 201 ? 4 : 0);
            assertThat(activePledged(rice)).as("round %d", round).isEqualTo(expected).isLessThanOrEqualTo(10);
            assertThat(cancelledQuantity(line)).as("round %d", round).isEqualTo(4);
        }
    }

    @Test
    void concurrentCancellationsOfOneLineNeverCancelMoreThanIsOutstanding() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin, RICE_10);
        JsonNode pledged = created(pledge(donor, campaignId, body(campaignItemIds(campaignId).getFirst(), 5), newKey()));
        UUID contributionId = id(pledged);
        UUID line = id(pledged.path("items").get(0));

        List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(() -> cancel(donor, contributionId, cancelBody("Concurrent", line, 2), newKey()));
        }
        var responses = race(calls);

        // 5 pledged: two cancellations of 2 fit, the other two must see outstanding 1 and fail.
        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200, 409);
        assertThat(responses.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(2);
        responses.stream().filter(r -> r.statusCode() == 409).forEach(r -> problem(r, 409, "QUANTITY_EXCEEDED"));
        assertThat(cancelledQuantity(line)).isEqualTo(4);
        assertThat(reductionCount(contributionId)).isEqualTo(2);
        assertThat(sequences(caseOf(campaignId)))
                .containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2", "COMMITMENT_REDUCED:3", "COMMITMENT_REDUCED:4");
    }

    @Test
    void sameIdempotencyKeyFiredConcurrentlyCreatesExactlyOnePledge() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin, RICE_10);
        var request = body(campaignItemIds(campaignId).getFirst(), 3);
        String key = newKey();

        List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> pledge(donor, campaignId, request, key));
        }
        var responses = race(calls);

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
        assertThat(responses.stream()
                .filter(r -> r.headers().firstValue("Idempotent-Replayed").isPresent()).count()).isEqualTo(7);
        JsonNode original = node(responses.getFirst());
        responses.forEach(r -> assertThat(node(r)).isEqualTo(original));

        assertThat(count("contributions", "donor_user_id", donor.id())).isEqualTo(1);
        assertThat(sequences(caseOf(campaignId))).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2");
    }

    @Test
    void parallelPledgesOnDifferentItemsOfOneCaseGetUniqueGaplessSequences() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        List<Map<String, Object>> needs = new ArrayList<>();
        for (String name : List.of("Rice", "Water", "Soap", "Blanket", "Oil")) {
            needs.add(Map.of("name", name, "unit", "PIECE", "quantity", 100));
        }
        UUID campaignId = publishedCampaign(orgAdmin, needs);
        List<UUID> items = campaignItemIds(campaignId);
        List<Actor> donors = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            donors.add(donor(true));
        }

        int rounds = 3;
        for (int round = 0; round < rounds; round++) {
            List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
            for (int i = 0; i < donors.size(); i++) {
                Actor d = donors.get(i);
                UUID item = items.get(i % items.size());
                calls.add(() -> pledge(d, campaignId, body(item, 1), newKey()));
            }
            // Different items do not block each other at item level; only the case lock orders them.
            assertThat(race(calls)).extracting(HttpResponse::statusCode).as("round %d", round).containsOnly(201);
        }

        List<String> expected = new ArrayList<>(List.of("CASE_VERIFIED:1"));
        for (int sequence = 2; sequence <= rounds * donors.size() + 1; sequence++) {
            expected.add("AID_PLEDGED:" + sequence);
        }
        assertThat(sequences(caseOf(campaignId))).containsExactlyElementsOf(expected);
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> pledge(Actor actor, UUID campaignId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments", body, key, true);
    }

    private HttpResponse<String> cancel(Actor actor, UUID contributionId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/commitments/" + contributionId + "/cancellations", body, key, true);
    }

    private JsonNode created(HttpResponse<String> response) {
        status(response, 201);
        return node(response);
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
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

    /** Releases every call at the same instant; invariants are then checked in the database. */
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

    private long activePledged(UUID campaignItemId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(pledged_quantity - cancelled_quantity), 0)
                FROM contribution_items WHERE campaign_item_id = ?
                """, Long.class, campaignItemId);
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
}