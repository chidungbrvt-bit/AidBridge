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
class ContributionOutboxIT extends AbstractCampaignHttpIT {

    @Test
    void pledgeWritesOneAuditEventAndOnePendingJobAfterTheCaseVerification() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID caseId = caseOf(campaignId);
        List<UUID> items = campaignItemIds(campaignId);

        var created = pledge(donor, campaignId, body(items.get(0), 2, items.get(1), 1), newKey());
        status(created, 201);
        JsonNode response = node(created);
        UUID contributionId = UUID.fromString(response.path("id").asText());
        UUID auditEventId = UUID.fromString(response.path("auditEventId").asText());
        assertThat(response.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");

        assertThat(jdbc.queryForMap("""
                SELECT a.event_type, a.entity_type, a.entity_id, a.event_key, a.actor_user_id,
                       a.organization_id, a.aid_case_id, j.status, j.chain_sequence,
                       j.aid_case_id AS job_case_id, octet_length(j.logical_event_id) AS event_id_len
                FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ?
                """, auditEventId))
                .containsEntry("event_type", "AID_PLEDGED")
                .containsEntry("entity_type", "CONTRIBUTION")
                .containsEntry("entity_id", contributionId)
                .containsEntry("event_key", contributionId)
                .containsEntry("actor_user_id", donor.id())
                .containsEntry("organization_id", ORG_A)
                .containsEntry("aid_case_id", caseId)
                .containsEntry("status", "PENDING")
                .containsEntry("chain_sequence", 2L)
                .containsEntry("job_case_id", caseId)
                .containsEntry("event_id_len", 32);

        // The B05 CASE_VERIFIED job (old constructor) now carries its case too, so it is counted.
        assertThat(sequences(caseId)).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2");

        String payload = jdbc.queryForObject(
                "SELECT payload_private::text FROM audit_events WHERE id = ?", String.class, auditEventId);
        JsonNode privatePayload = json.readTree(payload);
        assertThat(privatePayload.path("payloadVersion").asInt()).isEqualTo(1);
        assertThat(privatePayload.path("contributionId").asText()).isEqualTo(contributionId.toString());
        assertThat(privatePayload.path("lines").size()).isEqualTo(2);
        assertThat(payload).doesNotContain("@example.com", "B04 Test Staff", "Demo Household A");
    }

    @Test
    void pledgesOnOneCaseReceiveConsecutiveSequencesIndependentOfOtherCases() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);

        status(pledge(donorA, campaignId, body(items.get(0), 1), newKey()), 201);
        status(pledge(donorB, campaignId, body(items.get(1), 1), newKey()), 201);
        status(pledge(donorB, campaignId, body(items.get(0), 1), newKey()), 201);
        assertThat(sequences(caseOf(campaignId)))
                .containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2", "AID_PLEDGED:3", "AID_PLEDGED:4");

        UUID otherCampaign = publishedCampaign(orgAdmin);
        status(pledge(donorA, otherCampaign, body(campaignItemIds(otherCampaign).getFirst(), 1), newKey()), 201);
        assertThat(sequences(caseOf(otherCampaign))).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2");
    }

    @Test
    void rejectedPledgeWritesNoAuditEventAndNoJob() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID caseId = caseOf(campaignId);
        List<UUID> items = campaignItemIds(campaignId);

        problem(pledge(donor, campaignId, body(items.get(0), 2, items.get(1), 3), newKey()), 409, "QUANTITY_EXCEEDED");

        assertThat(auditCount(caseId, "AID_PLEDGED")).isZero();
        assertThat(sequences(caseId)).containsExactly("CASE_VERIFIED:1");
    }

    @Test
    void replayReturnsTheSameAuditEventWithoutWritingAnotherOne() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID caseId = caseOf(campaignId);
        var request = body(campaignItemIds(campaignId).getFirst(), 1);
        String key = newKey();

        var first = pledge(donor, campaignId, request, key);
        status(first, 201);
        var replay = pledge(donor, campaignId, request, key);
        status(replay, 201);

        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(node(replay).path("auditEventId")).isEqualTo(node(first).path("auditEventId"));
        assertThat(auditCount(caseId, "AID_PLEDGED")).isEqualTo(1);
        assertThat(sequences(caseId)).containsExactly("CASE_VERIFIED:1", "AID_PLEDGED:2");
    }

    @Test
    void publicCampaignReadsShowLiveActivePledgeTotals() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donorA = donor(true), donorB = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID untouched = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        status(pledge(donorA, campaignId, body(items.get(0), 2), newKey()), 201);
        status(pledge(donorB, campaignId, body(items.get(1), 1), newKey()), 201);

        // name|target|pledged|received|delivered|confirmed|remaining. Item order is not a contract yet (D50).
        Browser anonymous = new Browser();
        var detail = anonymous.send("GET", "/api/campaigns/" + campaignId, null, null, false);
        status(detail, 200);
        assertThat(progress(node(detail))).containsExactlyInAnyOrder("Food|2|2|0|0|0|0", "Hygiene|2|1|0|0|0|1");
        assertThat(detail.body()).doesNotContain(donorA.id().toString(), donorB.id().toString());

        assertThat(progress(findInPublicList(anonymous, campaignId)))
                .containsExactlyInAnyOrder("Food|2|2|0|0|0|0", "Hygiene|2|1|0|0|0|1");
        assertThat(progress(findInPublicList(anonymous, untouched)))
                .containsExactlyInAnyOrder("Food|2|0|0|0|0|2", "Hygiene|2|0|0|0|0|2");
    }

    // ---------------------------------------------------------------- helpers
    // pledge/body mirror ContributionPledgeIT on purpose: same-named package methods in the base class
    // would clash with that class's private copies.

    private HttpResponse<String> pledge(Actor actor, UUID campaignId, Object body, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments", body, key, true);
    }

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

    private UUID caseOf(UUID campaignId) {
        return jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
    }

    /** "EVENT_TYPE:sequence" for every job bound to the case, in chain order. */
    private List<String> sequences(UUID caseId) {
        return jdbc.queryForList("""
                SELECT a.event_type || ':' || j.chain_sequence
                FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE j.aid_case_id = ?
                ORDER BY j.chain_sequence
                """, String.class, caseId);
    }

    private long auditCount(UUID caseId, String eventType) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = ?",
                Long.class, caseId, eventType);
    }

    private static List<String> progress(JsonNode campaign) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : campaign.path("items")) {
            result.add(String.join("|", item.path("name").asText(), item.path("targetQty").asText(),
                    item.path("pledgedQty").asText(), item.path("receivedQty").asText(),
                    item.path("deliveredQty").asText(), item.path("confirmedQty").asText(),
                    item.path("remainingPledgeQty").asText()));
        }
        return result;
    }

    /** Pages through the public list: IT classes share one schema, so page 0 may not hold the campaign. */
    private JsonNode findInPublicList(Browser browser, UUID campaignId) throws Exception {
        for (int page = 0; ; page++) {
            var response = browser.send("GET", "/api/campaigns?page=" + page + "&size=100", null, null, false);
            status(response, 200);
            JsonNode body = node(response);
            for (JsonNode campaign : body.path("items")) {
                if (campaign.path("id").asText().equals(campaignId.toString())) {
                    return campaign;
                }
            }
            if ((long) (page + 1) * 100 >= body.path("totalElements").asLong()) {
                throw new AssertionError("Campaign is not in the public list: " + campaignId);
            }
        }
    }
}
