package com.aidbridge.casefile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Published-campaign fixtures shared by B07+ ITs. CampaignApiIT keeps its own helpers untouched. */
abstract class AbstractCampaignHttpIT extends AbstractCaseHttpIT {
    static final String CAMPAIGN_TITLE = "Winter food and hygiene support";
    static final String CAMPAIGN_SUMMARY = "Households in the simulated district need food and hygiene supplies.";

    /** Creates a case through the API (needs Food/PACKAGE/2 and Hygiene/KIT/2) and approves it through B05. */
    UUID verifiedCase() throws Exception {
        return verifiedCase(null);
    }

    /** Same as verifiedCase(), with caller-provided need lines when {@code needs} is non-null. */
    UUID verifiedCase(List<Map<String, Object>> needs) throws Exception {
        Map<String, Object> body = createBody();
        if (needs != null) {
            body.put("needs", needs);
        }
        body.put("consent", Map.of(
                "basis", "CONSENT",
                "scope", "Aid case processing",
                "capturedAt", Instant.now().minusSeconds(60).toString(),
                "publicSummaryAllowed", true));
        var created = volunteer.browser().send("POST", "/api/cases", body, newKey(), true);
        status(created, 201);
        JsonNode submitted = node(created);

        List<String> evidenceIds = new ArrayList<>();
        JsonNode versions = submitted.path("evidenceVersions");
        for (int i = 0; i < versions.size(); i++) {
            evidenceIds.add(versions.get(i).path("id").asText());
        }
        UUID caseId = UUID.fromString(submitted.path("id").asText());
        var approved = verifier.browser().send("POST", "/api/cases/" + caseId + "/verify",
                Map.of("expectedVersion", submitted.path("version").asLong(),
                        "reviewedEvidenceVersionIds", evidenceIds),
                newKey(), true);
        status(approved, 200);
        return caseId;
    }

    /** Draft (P01) + publish (P02) through the real API. Returns the campaign ID. */
    UUID publishedCampaign(Actor orgAdmin) throws Exception {
        return publishedCampaign(orgAdmin, null);
    }

    UUID publishedCampaign(Actor orgAdmin, List<Map<String, Object>> needs) throws Exception {
        UUID campaignId = draftCampaign(orgAdmin, needs);
        status(orgAdmin.browser().send("POST", "/api/campaigns/" + campaignId + "/publish", null, newKey(), true), 200);
        return campaignId;
    }

    /** Draft only (P01). Each target equals the need quantity. */
    UUID draftCampaign(Actor orgAdmin, List<Map<String, Object>> needs) throws Exception {
        var created = orgAdmin.browser().send("POST", "/api/campaigns",
                Map.of("caseId", verifiedCase(needs), "title", CAMPAIGN_TITLE, "publicSummary", CAMPAIGN_SUMMARY),
                newKey(), true);
        status(created, 201);
        return UUID.fromString(node(created).path("id").asText());
    }

    /** Deterministic order (Food, Hygiene). D50 is undecided, so never rely on insertion order. */
    List<UUID> campaignItemIds(UUID campaignId) {
        return jdbc.queryForList("""
                SELECT id FROM campaign_items WHERE campaign_id = ?
                ORDER BY lower(item_name), lower(unit), id
                """, UUID.class, campaignId);
    }

    /** A logged-in account with no memberships; donor capability is granted explicitly (D52). */
    Actor donor(boolean enabled) throws Exception {
        Actor donor = actor(null, null, false);
        jdbc.update("UPDATE users SET donor_enabled = ? WHERE id = ?", enabled, donor.id());
        return donor;
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }
}