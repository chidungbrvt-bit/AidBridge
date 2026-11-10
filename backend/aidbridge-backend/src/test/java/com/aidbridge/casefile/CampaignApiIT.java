package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class CampaignApiIT extends AbstractCaseHttpIT {

    private static final String TITLE = "Winter food and hygiene support";
    private static final String SUMMARY = "Households in the simulated district need food and hygiene supplies.";

    // ---------------------------------------------------------------- BR03-P

    @Test
    void orgAdminCreatesDraftCopyingEveryApprovedNeedAndReplayIsIdempotent() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        String key = newKey();
        var body = campaignBody(caseId);

        var created = orgAdmin.browser().send("POST", "/api/campaigns", body, key, true);
        status(created, 201);
        var draft = node(created);
        UUID campaignId = UUID.fromString(draft.path("id").asText());

        assertThat(created.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/api/campaigns/" + campaignId));
        assertThat(created.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("title").asText()).isEqualTo(TITLE);
        assertThat(draft.path("publicSummary").asText()).isEqualTo(SUMMARY);
        assertThat(draft.path("approximateArea").asText()).isEqualTo("Simulated district");
        assertThat(draft.path("organization").path("name").asText()).isEqualTo("B04 Organization A");
        assertThat(draft.path("organization").path("publicRef").asText()).matches("0x[0-9a-f]{64}");
        assertThat(draft.path("auditUrl").asText()).isNotBlank();
        assertThat(itemSignatures(draft)).containsExactlyInAnyOrder(
                "Food|PACKAGE|2|0|0|0|0|2",
                "Hygiene|KIT|2|0|0|0|0|2");

        // The public DTO must never expose private case data (BR-08).
        assertThat(created.body()).doesNotContain(caseId.toString(), "Demo Household A", "Private case title");

        // DB effects: one DRAFT campaign, two items that point at this case's need lines, one audit event.
        assertThat(jdbc.queryForMap("SELECT status, organization_id, aid_case_id FROM campaigns WHERE id = ?", campaignId))
                .containsEntry("status", "DRAFT")
                .containsEntry("organization_id", ORG_A)
                .containsEntry("aid_case_id", caseId);
        assertThat(count("campaign_items", "campaign_id", campaignId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM campaign_items ci
                JOIN case_need_items n ON n.id = ci.case_need_item_id
                WHERE ci.campaign_id = ? AND n.aid_case_id = ?
                  AND ci.item_name = n.item_name AND ci.unit = n.unit
                  AND ci.target_quantity = n.requested_quantity
                """, Long.class, campaignId, caseId)).isEqualTo(2L);
        assertThat(auditCount(caseId, "CAMPAIGN_CREATED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("VERIFIED");

        // Replay with the same key and same body: same result, no new rows.
        var replay = orgAdmin.browser().send("POST", "/api/campaigns", body, key, true);
        status(replay, 201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(node(replay)).isEqualTo(draft);
        assertThat(count("campaigns", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(count("campaign_items", "campaign_id", campaignId)).isEqualTo(2);
        assertThat(auditCount(caseId, "CAMPAIGN_CREATED")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- BR11-P

    @Test
    void orgAdminPublishesDraftMovingCaseToInProgressAndReplayIsIdempotent() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        UUID campaignId = createDraft(orgAdmin, caseId);
        String key = newKey();
        long jobsBefore = blockchainJobCount(caseId);

        var published = orgAdmin.browser().send("POST", "/api/campaigns/" + campaignId + "/publish", null, key, true);
        status(published, 200);
        var body = node(published);
        assertThat(body.path("id").asText()).isEqualTo(campaignId.toString());
        assertThat(body.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(itemSignatures(body)).containsExactlyInAnyOrder(
                "Food|PACKAGE|2|0|0|0|0|2",
                "Hygiene|KIT|2|0|0|0|0|2");

        assertThat(jdbc.queryForMap("SELECT status, published_at FROM campaigns WHERE id = ?", campaignId))
                .containsEntry("status", "PUBLISHED")
                .doesNotContainEntry("published_at", null);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("IN_PROGRESS");
        assertThat(auditCount(caseId, "CAMPAIGN_PUBLISHED")).isEqualTo(1);
        // CAMPAIGN_* events are local audit only: they must not enqueue blockchain jobs.
        assertThat(blockchainJobCount(caseId)).isEqualTo(jobsBefore);

        var replay = orgAdmin.browser().send("POST", "/api/campaigns/" + campaignId + "/publish", null, key, true);
        status(replay, 200);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(node(replay)).isEqualTo(body);
        assertThat(auditCount(caseId, "CAMPAIGN_PUBLISHED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("IN_PROGRESS");
    }

    // ---------------------------------------------------------------- BR03-N

    @Test
    void caseThatIsNotVerifiedCannotBecomeACampaign() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);

        JsonNode submitted = submittedCase(true);

        JsonNode rejected = submittedCase(true);
        review(verifier, rejected, "reject", "Household moved outside coverage area");

        JsonNode changesRequested = submittedCase(true);
        review(verifier, changesRequested, "request-changes", "Clarify household size");

        for (JsonNode aidCase : List.of(submitted, rejected, changesRequested)) {
            UUID caseId = caseId(aidCase);
            problem(createCampaign(orgAdmin, caseId, newKey()), 409, "INVALID_STATE");
            assertNoCampaign(caseId);
        }
    }

    @Test
    void secondCampaignForTheSameCaseIsRejected() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        UUID firstCampaign = createDraft(orgAdmin, caseId);

        problem(createCampaign(orgAdmin, caseId, newKey()), 409, "INVALID_STATE");

        assertThat(count("campaigns", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(count("campaign_items", "campaign_id", firstCampaign)).isEqualTo(2);
        assertThat(auditCount(caseId, "CAMPAIGN_CREATED")).isEqualTo(1);
    }

    @Test
    void caseWithoutPublicSummaryConsentCannotBecomeACampaign() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(false);

        problem(createCampaign(orgAdmin, caseId, newKey()), 409, "INVALID_STATE");
        assertNoCampaign(caseId);
    }

    @Test
    void activeDisputeBlocksDraftCreationAndPublishButResolvedDisputeDoesNot() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);

        UUID blockedDraft = verifiedCase(true);
        openDispute(blockedDraft, orgAdmin, "OPEN");
        problem(createCampaign(orgAdmin, blockedDraft, newKey()), 409, "INVALID_STATE");
        assertNoCampaign(blockedDraft);

        UUID blockedPublish = verifiedCase(true);
        UUID draftId = createDraft(orgAdmin, blockedPublish);
        openDispute(blockedPublish, orgAdmin, "UNDER_REVIEW");
        problem(publishCampaign(orgAdmin, draftId, newKey()), 409, "INVALID_STATE");
        assertThat(campaignStatus(draftId)).isEqualTo("DRAFT");
        assertThat(caseStatus(blockedPublish)).isEqualTo("VERIFIED");
        assertThat(auditCount(blockedPublish, "CAMPAIGN_PUBLISHED")).isZero();

        UUID resolvedOnly = verifiedCase(true);
        openDispute(resolvedOnly, orgAdmin, "RESOLVED");
        createDraft(orgAdmin, resolvedOnly); // asserts 201
    }

    @Test
    void onlyOrgAdminOfTheOwningOrganizationCanCreateOrPublish() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor foreignAdmin = actor(ORG_B, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);

        problem(createCampaign(volunteer, caseId, newKey()), 403, "FORBIDDEN");
        problem(createCampaign(verifier, caseId, newKey()), 403, "FORBIDDEN");
        problem(createCampaign(foreignAdmin, caseId, newKey()), 404, "RESOURCE_NOT_FOUND");
        assertNoCampaign(caseId);

        UUID campaignId = createDraft(orgAdmin, caseId);
        problem(publishCampaign(volunteer, campaignId, newKey()), 403, "FORBIDDEN");
        problem(publishCampaign(verifier, campaignId, newKey()), 403, "FORBIDDEN");
        problem(publishCampaign(foreignAdmin, campaignId, newKey()), 404, "RESOURCE_NOT_FOUND");
        assertThat(campaignStatus(campaignId)).isEqualTo("DRAFT");
        assertThat(caseStatus(caseId)).isEqualTo("VERIFIED");
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        String key = newKey();

        status(createCampaign(orgAdmin, caseId, key), 201);

        var changed = Map.<String, Object>of("caseId", caseId, "title", TITLE + " changed", "publicSummary", SUMMARY);
        problem(orgAdmin.browser().send("POST", "/api/campaigns", changed, key, true), 409, "IDEMPOTENCY_CONFLICT");

        assertThat(count("campaigns", "aid_case_id", caseId)).isEqualTo(1);
        assertThat(auditCount(caseId, "CAMPAIGN_CREATED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM campaigns WHERE aid_case_id = ?", String.class, caseId))
                .isEqualTo(TITLE);
    }

    @Test
    void caseModifiedAfterApprovalCannotBecomeACampaign() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);

        // Simulate any write that happened after the review (version moves past reviewed + 1).
        jdbc.update("UPDATE aid_cases SET version = version + 1 WHERE id = ?", caseId);

        problem(createCampaign(orgAdmin, caseId, newKey()), 409, "VERSION_CONFLICT");
        assertNoCampaign(caseId);
    }

    @Test
    void needsChangedAfterDraftMakePublishStale() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        UUID campaignId = createDraft(orgAdmin, caseId);

        jdbc.update("""
                UPDATE case_need_items SET requested_quantity = requested_quantity + 1
                WHERE aid_case_id = ? AND retired_at_version IS NULL
                """, caseId);

        problem(publishCampaign(orgAdmin, campaignId, newKey()), 409, "VERSION_CONFLICT");
        assertThat(campaignStatus(campaignId)).isEqualTo("DRAFT");
        assertThat(caseStatus(caseId)).isEqualTo("VERIFIED");
        assertThat(auditCount(caseId, "CAMPAIGN_PUBLISHED")).isZero();
    }

    // ---------------------------------------------------------------- BR08 + draft isolation (P03/P04)

    @Test
    void publicListShowsOnlyPublicCampaignsOfApprovedOrganizations() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID draftId = createDraft(orgAdmin, verifiedCase(true));
        UUID publishedId = publishedCampaign(orgAdmin);
        UUID fulfilledId = publishedCampaign(orgAdmin);
        UUID closedId = publishedCampaign(orgAdmin);
        jdbc.update("UPDATE campaigns SET status = 'FULFILLED' WHERE id = ?", fulfilledId);
        jdbc.update("UPDATE campaigns SET status = 'CLOSED', closed_at = now() WHERE id = ?", closedId);

        Browser anonymous = new Browser();
        var listed = get(anonymous, "/api/campaigns?page=0&size=100");
        status(listed, 200);
        var page = node(listed);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(100);
        assertThat(page.path("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(listedIds(page)).contains(publishedId, fulfilledId, closedId).doesNotContain(draftId);

        // A suspended organization disappears from both the list and the detail endpoint.
        jdbc.update("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?", ORG_A);
        var afterSuspension = get(anonymous, "/api/campaigns?page=0&size=100");
        status(afterSuspension, 200);
        assertThat(listedIds(node(afterSuspension))).doesNotContain(publishedId, fulfilledId, closedId, draftId);
        problem(get(anonymous, "/api/campaigns/" + publishedId), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void publicListRejectsInvalidPaging() throws Exception {
        Browser anonymous = new Browser();
        problem(get(anonymous, "/api/campaigns?page=-1&size=20"), 400, "VALIDATION_ERROR");
        problem(get(anonymous, "/api/campaigns?page=0&size=0"), 400, "VALIDATION_ERROR");
        problem(get(anonymous, "/api/campaigns?page=0&size=101"), 400, "VALIDATION_ERROR");
        status(get(anonymous, "/api/campaigns?page=0&size=100"), 200);
        status(get(anonymous, "/api/campaigns"), 200); // defaults page=0, size=20
    }

    @Test
    void draftDetailIsVisibleOnlyToOwningOrgAdmin() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor foreignAdmin = actor(ORG_B, "ORG_ADMIN", false);
        UUID draftId = createDraft(orgAdmin, verifiedCase(true));
        String path = "/api/campaigns/" + draftId;

        var own = get(orgAdmin.browser(), path);
        status(own, 200);
        assertThat(node(own).path("status").asText()).isEqualTo("DRAFT");

        problem(get(new Browser(), path), 404, "RESOURCE_NOT_FOUND");
        problem(get(volunteer.browser(), path), 404, "RESOURCE_NOT_FOUND");
        problem(get(verifier.browser(), path), 404, "RESOURCE_NOT_FOUND");
        problem(get(foreignAdmin.browser(), path), 404, "RESOURCE_NOT_FOUND");
        problem(get(new Browser(), "/api/campaigns/" + UUID.randomUUID()), 404, "RESOURCE_NOT_FOUND");

        status(publishCampaign(orgAdmin, draftId, newKey()), 200);
        var publicView = get(new Browser(), path);
        status(publicView, 200);
        assertThat(node(publicView).path("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void publicPayloadNeverLeaksPrivateCaseOrPersonData() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCase(true);
        UUID campaignId = createDraft(orgAdmin, caseId);
        status(publishCampaign(orgAdmin, campaignId, newKey()), 200);

        Browser anonymous = new Browser();
        var detail = get(anonymous, "/api/campaigns/" + campaignId);
        var list = get(anonymous, "/api/campaigns?page=0&size=100");
        status(detail, 200);
        status(list, 200);

        for (var response : List.of(detail, list)) {
            assertThat(response.body()).doesNotContain(
                    caseId.toString(), "Demo Household A", "Private case title",
                    "household-note", "consent-note", "@example.com",
                    orgAdmin.id().toString(), volunteer.id().toString(), verifier.id().toString(),
                    ORG_A.toString());
        }

        var body = node(detail);
        for (String privateField : List.of("caseId", "aidCaseId", "beneficiaryAlias", "internalTitle",
                "createdBy", "evidenceFixtureKeys", "consent")) {
            assertThat(body.has(privateField)).as("public field " + privateField).isFalse();
        }
        assertThat(body.path("organization").has("id")).isFalse();
        assertThat(body.path("organization").path("publicRef").asText()).matches("0x[0-9a-f]{64}");
    }

    // ---------------------------------------------------------------- regression: item order

    @Test
    void publishSucceedsRegardlessOfItemOrderForManyNeedLines() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID caseId = verifiedCaseWithNeeds(List.of(
                Map.of("name", "Rice", "unit", "BAG", "quantity", 3),
                Map.of("name", "Cooking oil", "unit", "BOTTLE", "quantity", 2),
                Map.of("name", "Blanket", "unit", "PIECE", "quantity", 4),
                Map.of("name", "Soap", "unit", "BAR", "quantity", 10),
                Map.of("name", "Drinking water", "unit", "LITRE", "quantity", 20)));
        UUID campaignId = createDraft(orgAdmin, caseId);

        var published = publishCampaign(orgAdmin, campaignId, newKey());
        status(published, 200);
        assertThat(itemSignatures(node(published))).containsExactlyInAnyOrder(
                "Rice|BAG|3|0|0|0|0|3",
                "Cooking oil|BOTTLE|2|0|0|0|0|2",
                "Blanket|PIECE|4|0|0|0|0|4",
                "Soap|BAR|10|0|0|0|0|10",
                "Drinking water|LITRE|20|0|0|0|0|20");
        assertThat(campaignStatus(campaignId)).isEqualTo("PUBLISHED");
    }

    // ---------------------------------------------------------------- helpers

    /** Creates a case through the real API and approves it through the real B05 endpoint. */
    private UUID verifiedCase(boolean publicSummaryAllowed) throws Exception {
        Map<String, Object> body = createBody();
        body.put("consent", Map.of(
                "basis", "CONSENT",
                "scope", "Aid case processing",
                "capturedAt", Instant.now().minusSeconds(60).toString(),
                "publicSummaryAllowed", publicSummaryAllowed));
        var created = volunteer.browser().send("POST", "/api/cases", body, newKey(), true);
        status(created, 201);
        JsonNode submitted = node(created);

        List<String> evidenceIds = new ArrayList<>();
        JsonNode versions = submitted.path("evidenceVersions");
        for (int i = 0; i < versions.size(); i++) {
            evidenceIds.add(versions.get(i).path("id").asText());
        }
        UUID caseId = UUID.fromString(submitted.path("id").asText());
        HttpResponse<String> approved = verifier.browser().send("POST", "/api/cases/" + caseId + "/verify",
                Map.of("expectedVersion", submitted.path("version").asLong(),
                        "reviewedEvidenceVersionIds", evidenceIds),
                newKey(), true);
        status(approved, 200);
        assertThat(node(approved).path("caseStatus").asText()).isEqualTo("VERIFIED");
        return caseId;
    }

    private UUID createDraft(Actor orgAdmin, UUID caseId) throws Exception {
        var created = orgAdmin.browser().send("POST", "/api/campaigns", campaignBody(caseId), newKey(), true);
        status(created, 201);
        return UUID.fromString(node(created).path("id").asText());
    }

    private static Map<String, Object> campaignBody(UUID caseId) {
        return Map.of("caseId", caseId, "title", TITLE, "publicSummary", SUMMARY);
    }

    /** name|unit|target|pledged|received|delivered|confirmed|remaining, one string per item. */
    private static List<String> itemSignatures(JsonNode campaign) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : campaign.path("items")) {
            result.add(String.join("|",
                    item.path("name").asText(), item.path("unit").asText(),
                    item.path("targetQty").asText(), item.path("pledgedQty").asText(),
                    item.path("receivedQty").asText(), item.path("deliveredQty").asText(),
                    item.path("confirmedQty").asText(), item.path("remainingPledgeQty").asText()));
        }
        return result;
    }

    private long auditCount(UUID caseId, String eventType) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = ?",
                Long.class, caseId, eventType);
    }

    private long blockchainJobCount(UUID caseId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE a.aid_case_id = ?
                """, Long.class, caseId);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Creates a case through the API and leaves it SUBMITTED (no review). */
    private JsonNode submittedCase(boolean publicSummaryAllowed) throws Exception {
        Map<String, Object> body = createBody();
        body.put("consent", Map.of(
                "basis", "CONSENT",
                "scope", "Aid case processing",
                "capturedAt", Instant.now().minusSeconds(60).toString(),
                "publicSummaryAllowed", publicSummaryAllowed));
        var created = volunteer.browser().send("POST", "/api/cases", body, newKey(), true);
        status(created, 201);
        return node(created);
    }

    /** Runs reject or request-changes as the given reviewer; both require a reason. */
    private void review(Actor reviewer, JsonNode submitted, String action, String reason) throws Exception {
        var response = reviewer.browser().send("POST", "/api/cases/" + caseId(submitted) + "/" + action,
                Map.of("expectedVersion", submitted.path("version").asLong(),
                        "reviewedEvidenceVersionIds", evidenceIds(submitted),
                        "reason", reason),
                newKey(), true);
        status(response, 200);
    }

    private HttpResponse<String> createCampaign(Actor actor, UUID caseId, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns", campaignBody(caseId), key, true);
    }

    private HttpResponse<String> publishCampaign(Actor actor, UUID campaignId, String key) throws Exception {
        return actor.browser().send("POST", "/api/campaigns/" + campaignId + "/publish", null, key, true);
    }

    private void openDispute(UUID caseId, Actor openedBy, String status) {
        jdbc.update("INSERT INTO disputes (aid_case_id, opened_by, status, reason) VALUES (?, ?, ?, 'Synthetic dispute')",
                caseId, openedBy.id(), status);
    }

    private void assertNoCampaign(UUID caseId) {
        assertThat(count("campaigns", "aid_case_id", caseId)).isZero();
        assertThat(auditCount(caseId, "CAMPAIGN_CREATED")).isZero();
    }

    private String campaignStatus(UUID campaignId) {
        return jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, campaignId);
    }

    private String caseStatus(UUID caseId) {
        return jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, caseId);
    }

    private static UUID caseId(JsonNode aidCase) {
        return UUID.fromString(aidCase.path("id").asText());
    }

    private static List<String> evidenceIds(JsonNode aidCase) {
        List<String> ids = new ArrayList<>();
        JsonNode versions = aidCase.path("evidenceVersions");
        for (int i = 0; i < versions.size(); i++) {
            ids.add(versions.get(i).path("id").asText());
        }
        return ids;
    }

    private UUID publishedCampaign(Actor orgAdmin) throws Exception {
        UUID campaignId = createDraft(orgAdmin, verifiedCase(true));
        status(publishCampaign(orgAdmin, campaignId, newKey()), 200);
        return campaignId;
    }

    private HttpResponse<String> get(Browser browser, String path) throws Exception {
        return browser.send("GET", path, null, null, false);
    }

    private static List<UUID> listedIds(JsonNode page) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : page.path("items")) {
            ids.add(UUID.fromString(item.path("id").asText()));
        }
        return ids;
    }

    /** Same as verifiedCase(true) but with a caller-provided list of need lines. */
    private UUID verifiedCaseWithNeeds(List<Map<String, Object>> needs) throws Exception {
        Map<String, Object> body = createBody();
        body.put("needs", needs);
        body.put("consent", Map.of(
                "basis", "CONSENT",
                "scope", "Aid case processing",
                "capturedAt", Instant.now().minusSeconds(60).toString(),
                "publicSummaryAllowed", true));
        var created = volunteer.browser().send("POST", "/api/cases", body, newKey(), true);
        status(created, 201);
        JsonNode submitted = node(created);

        UUID caseId = caseId(submitted);
        var approved = verifier.browser().send("POST", "/api/cases/" + caseId + "/verify",
                Map.of("expectedVersion", submitted.path("version").asLong(),
                        "reviewedEvidenceVersionIds", evidenceIds(submitted)),
                newKey(), true);
        status(approved, 200);
        return caseId;
    }
}