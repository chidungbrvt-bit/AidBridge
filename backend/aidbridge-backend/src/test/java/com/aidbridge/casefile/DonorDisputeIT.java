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

/** S02 through HTTP and the isolated PostgreSQL test schema. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DonorDisputeIT extends AbstractDeliveryHttpIT {

    @Test
    void opensCaseLevelDisputeAndStoresOnlyTheDonorSummaryForReplay() throws Exception {
        Actor owner = donor(true);
        Funding funding = fundCampaign(actor(ORG_A, "ORG_ADMIN", false), owner);
        String key = newKey();
        String reason = "The campaign figures need another review.";
        var body = Map.of("reason", "  " + reason + "  ");
        var sequenceBefore = sequences(funding.caseId());

        var first = open(owner, funding.campaignId(), body, key);
        status(first, 201);
        JsonNode response = node(first);
        UUID disputeId = UUID.fromString(response.path("id").asText());
        String createdAt = response.path("createdAt").asText();
        assertThat(Instant.parse(createdAt)).isNotNull();
        assertThat(response).isEqualTo(json.readTree(json.writeValueAsString(Map.of(
                "id", disputeId, "status", "OPEN", "createdAt", createdAt
        ))));
        assertThat(first.headers().firstValue("Location")).contains("/api/disputes/" + disputeId);
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(jdbc.queryForMap("""
                SELECT aid_case_id, opened_by, delivery_id, reason, status FROM disputes WHERE id = ?
                """, disputeId)).containsEntry("aid_case_id", funding.caseId())
                .containsEntry("opened_by", owner.id()).containsEntry("delivery_id", null)
                .containsEntry("reason", reason).containsEntry("status", "OPEN");

        var replay = open(owner, funding.campaignId(), body, key);
        status(replay, 201);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(open(owner, funding.campaignId(), Map.of("reason", "A different issue."), key),
                409, "IDEMPOTENCY_CONFLICT");

        assertThat(count("disputes", "aid_case_id", funding.caseId())).isEqualTo(1);
        assertThat(openedAuditCount(funding.caseId())).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT actor_user_id, entity_id FROM audit_events WHERE event_key = ?", disputeId))
                .containsEntry("actor_user_id", owner.id()).containsEntry("entity_id", disputeId);
        assertThat(sequences(funding.caseId())).isEqualTo(sequenceBefore);
        String journal = jdbc.queryForObject("""
                SELECT response_private::text FROM idempotency_records
                WHERE actor_id = ? AND organization_id = ? AND operation = ? AND key = ?
                """, String.class, owner.id(), ORG_A, "POST:" + route(funding.campaignId()), key);
        assertThat(json.readTree(journal)).isEqualTo(response);
    }

    @Test
    void requiresAContributionInThisCampaignAndRechecksTheDonorGrantOnReplay() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor owner = donor(true);
        Actor otherDonor = donor(true);
        Funding funded = fundCampaign(orgAdmin, owner);
        fundCampaign(orgAdmin, otherDonor);
        var body = Map.of("reason", "The campaign figures need another review.");
        String key = newKey();

        problem(open(otherDonor, funded.campaignId(), body, key), 404, "RESOURCE_NOT_FOUND");
        problem(open(owner, draftCampaign(orgAdmin, null), body, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(open(orgAdmin, funded.campaignId(), body, newKey()), 403, "FORBIDDEN");
        problem(open(admin, funded.campaignId(), body, newKey()), 403, "FORBIDDEN");
        assertThat(count("disputes", "aid_case_id", funded.caseId())).isZero();

        status(open(owner, funded.campaignId(), body, key), 201);
        jdbc.update("UPDATE users SET donor_enabled = FALSE WHERE id = ?", owner.id());
        problem(open(owner, funded.campaignId(), body, key), 403, "FORBIDDEN");
        assertThat(count("disputes", "aid_case_id", funded.caseId())).isEqualTo(1);
        assertThat(openedAuditCount(funded.caseId())).isEqualTo(1);
    }

    @Test
    void invalidRequestOrMissingCsrfLeavesDisputesAndAuditUnchanged() throws Exception {
        Actor owner = donor(true);
        Funding funding = fundCampaign(actor(ORG_A, "ORG_ADMIN", false), owner);
        UUID campaignId = funding.campaignId();
        var validBody = Map.of("reason", "The campaign figures need another review.");

        problem(open(owner, campaignId, Map.of("reason", "  "), newKey()), 400, "VALIDATION_ERROR");
        problem(open(owner, campaignId, validBody, null), 400, "VALIDATION_ERROR");
        problem(owner.browser().send("POST", route(campaignId), validBody, newKey(), false), 403, "CSRF_INVALID");
        assertThat(count("disputes", "aid_case_id", funding.caseId())).isZero();
        assertThat(openedAuditCount(funding.caseId())).isZero();
    }

    @Test
    void donorDisputeReopensACompletedCaseAndBlocksClosingAgain() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor owner = donor(true);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Funding funding = fundCampaign(orgAdmin, owner);
        UUID campaignId = funding.campaignId();

        var receiptLines = funding.itemIds().stream().map(itemId -> Map.of(
                "contributionItemId", lineFor(funding.contribution(), itemId), "quantity", 2
        )).toList();
        String contributionId = funding.contribution().path("id").asText();
        status(orgAdmin.browser().send("POST", "/api/commitments/" + contributionId + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", receiptLines), newKey(), true), 201);

        var evidenceIds = jdbc.queryForList("""
                SELECT evidence_version_id FROM case_evidence_links
                WHERE aid_case_id = ? AND retired_at_version IS NULL
                """, UUID.class, funding.caseId());
        var delivered = deliver(volunteer, funding.caseId(),
                delivered(evidenceIds, funding.itemIds().get(0), 2, funding.itemIds().get(1), 2), newKey());
        status(delivered, 201);
        String deliveryId = node(delivered).path("id").asText();
        status(reviewer.browser().send("POST", "/api/deliveries/" + deliveryId + "/confirm",
                Map.of("decision", "CONFIRMED", "reviewedEvidenceVersionIds", evidenceIds), newKey(), true), 200);
        String closeRoute = "/api/campaigns/" + campaignId + "/close";
        status(orgAdmin.browser().send("POST", closeRoute, Map.of(), newKey(), true), 200);

        var opened = open(owner, campaignId, Map.of("reason", "The completed campaign needs review."), newKey());
        status(opened, 201);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, funding.caseId()))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForMap("SELECT status, closed_at FROM campaigns WHERE id = ?", campaignId))
                .containsEntry("status", "FULFILLED").containsEntry("closed_at", null);
        problem(orgAdmin.browser().send("POST", closeRoute, Map.of(), newKey(), true), 409, "DISPUTE_OPEN");
        assertThat(openedAuditCount(funding.caseId())).isEqualTo(1);
    }

    private Funding fundCampaign(Actor orgAdmin, Actor donor) throws Exception {
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> itemIds = campaignItemIds(campaignId);
        var items = itemIds.stream().map(itemId -> Map.of("campaignItemId", itemId, "quantity", 2)).toList();
        var pledge = donor.browser().send("POST", "/api/campaigns/" + campaignId + "/commitments",
                Map.of("items", items), newKey(), true);
        status(pledge, 201);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        return new Funding(caseId, campaignId, itemIds, node(pledge));
    }

    private HttpResponse<String> open(Actor donor, UUID campaignId, Object body, String key) throws Exception {
        return donor.browser().send("POST", route(campaignId), body, key, true);
    }

    private long openedAuditCount(UUID caseId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'DISPUTE_OPENED'
                """, Long.class, caseId);
    }

    private static String route(UUID campaignId) {
        return "/api/campaigns/" + campaignId + "/disputes";
    }

    private record Funding(UUID caseId, UUID campaignId, List<UUID> itemIds, JsonNode contribution) {
    }
}
