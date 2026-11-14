package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** T02 GET /api/campaigns/{id}/audit-trail: public, published campaigns only, no private data. */
class PublicAuditTrailIT extends AbstractAuditTrailHttpIT {

    @Test
    void publicTrailShowsAllowListedEventsWithHonestChainStatusAndNoPrivateData() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        Instant base = Instant.now().plus(1, ChronoUnit.HOURS);

        UUID confirmed = event(caseId, "AID_PLEDGED", base.plusSeconds(1), donor.id());
        String confirmedHash = hash();
        tx(job(confirmed, caseId, "CONFIRMED", null, 201), confirmedHash, "CONFIRMED", 4321L, 43113);

        UUID submitted = event(caseId, "AID_PLEDGED", base.plusSeconds(2), donor.id());
        String submittedHash = hash();
        tx(job(submitted, caseId, "SUBMITTED", null, 202), submittedHash, "SUBMITTED", null, 43113);

        UUID processing = event(caseId, "GOODS_RECEIVED", base.plusSeconds(3), orgAdmin.id());
        String preparedHash = hash();
        tx(job(processing, caseId, "PROCESSING", null, 203), preparedHash, "PREPARED", null, 43113);

        UUID reverted = event(caseId, "DELIVERY_RECORDED", base.plusSeconds(4), volunteer.id());
        String revertedHash = hash();
        tx(job(reverted, caseId, "FAILED", "TX_REVERTED", 204), revertedHash, "REVERTED", 4322L, 43113);

        UUID mismatch = event(caseId, "DELIVERY_RECORDED", base.plusSeconds(5), volunteer.id());
        String mismatchHash = hash();
        tx(job(mismatch, caseId, "FAILED", "RECEIPT_EVENT_ID_MISMATCH", 205), mismatchHash, "SUBMITTED", null, 43113);

        UUID internalOnly = event(caseId, "CASE_REJECTED", base.plusSeconds(6));
        UUID evidenceRead = event(caseId, "EVIDENCE_CONTENT_READ", base.plusSeconds(7));
        UUID failedAttempt = event(caseId, "DELIVERY_ATTEMPT_FAILED", base.plusSeconds(8), volunteer.id());

        var response = new Browser().send("GET", "/api/campaigns/" + campaignId + "/audit-trail?size=100",
                null, null, false);
        status(response, 200);
        JsonNode body = node(response);
        String raw = response.body();

        // Allow-list: on-chain milestones and the publication only.
        assertThat(types(body)).isSubsetOf("CASE_VERIFIED", "CAMPAIGN_PUBLISHED", "AID_PLEDGED",
                "GOODS_RECEIVED", "DELIVERY_RECORDED");
        assertThat(types(body)).contains("CASE_VERIFIED", "CAMPAIGN_PUBLISHED");
        assertThat(raw).doesNotContain(internalOnly.toString(), evidenceRead.toString(), failedAttempt.toString(),
                "CASE_CREATED", "CAMPAIGN_CREATED", "EVIDENCE_VERSION_CREATED");
        assertThat(body.path("totalElements").asLong()).isEqualTo(body.path("items").size());

        // Role labels instead of identities.
        assertThat(byType(body, "CASE_VERIFIED").path("publicActorRole").asText()).isEqualTo("VERIFIER");
        assertThat(byType(body, "CAMPAIGN_PUBLISHED").path("publicActorRole").asText()).isEqualTo("ORGANIZATION");
        assertThat(item(body, confirmed).path("publicActorRole").asText()).isEqualTo("DONOR");
        assertThat(item(body, processing).path("publicActorRole").asText()).isEqualTo("ORGANIZATION");
        assertThat(item(body, reverted).path("publicActorRole").asText()).isEqualTo("FIELD_VOLUNTEER");

        JsonNode c = item(body, confirmed).path("blockchain");
        assertThat(c.path("status").asText()).isEqualTo("CONFIRMED_ON_CHAIN");
        assertThat(c.path("txHash").asText()).isEqualTo(confirmedHash);
        assertThat(c.path("blockNumber").asLong()).isEqualTo(4321L);
        assertThat(c.path("explorerUrl").asText()).isEqualTo(SNOWTRACE + confirmedHash);
        assertThat(c.path("network").asText()).isEqualTo("Avalanche Fuji testnet");

        JsonNode s = item(body, submitted).path("blockchain");
        assertThat(s.path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(s.path("txHash").asText()).isEqualTo(submittedHash);
        assertAbsent(s, "blockNumber");

        JsonNode p = item(body, processing).path("blockchain");
        assertThat(p.path("status").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertAbsent(p, "txHash", "explorerUrl");

        JsonNode r = item(body, reverted).path("blockchain");
        assertThat(r.path("status").asText()).isEqualTo("FAILED_ON_CHAIN");
        assertThat(r.path("txHash").asText()).isEqualTo(revertedHash);
        assertThat(r.path("explorerUrl").asText()).isEqualTo(SNOWTRACE + revertedHash);

        JsonNode m = item(body, mismatch).path("blockchain");
        assertThat(m.path("status").asText()).isEqualTo("FAILED_ON_CHAIN");
        assertAbsent(m, "txHash", "explorerUrl", "blockNumber", "contractAddress");

        // Privacy regression on the whole JSON string.
        List<String> forbidden = new ArrayList<>(List.of(
                PAYLOAD_MARKER, MANIFEST_MARKER, preparedHash, mismatchHash,
                volunteer.id().toString(), verifier.id().toString(), orgAdmin.id().toString(), donor.id().toString(),
                ORG_A.toString(), caseId.toString(), "B04 Test Staff", "@example.com",
                "Demo Household", "Private case title", "Simulated district",
                "errorCode", "TX_REVERTED", "RECEIPT_", "actor\"", "fullName", "entityType",
                "payload", "manifest", "salt", "signer", "nonce", "consent", "evidence", "storageKey"));
        forbidden.addAll(jdbc.queryForList(
                "SELECT evidence_version_id::text FROM case_evidence_links WHERE aid_case_id = ?", String.class, caseId));
        forbidden.addAll(jdbc.queryForList(
                "SELECT consent_record_id::text FROM aid_cases WHERE id = ? AND consent_record_id IS NOT NULL",
                String.class, caseId));
        for (String value : forbidden) {
            assertThat(raw).as("public trail must not contain %s", value).doesNotContainIgnoringCase(value);
        }
    }

    @Test
    void onlyPublishedCampaignsOfApprovedOrganizationsAreVisible() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID draftId = draftCampaign(orgAdmin, null);
        UUID publishedId = publishedCampaign(orgAdmin);
        Browser anonymous = new Browser();

        problem(anonymous.send("GET", "/api/campaigns/" + draftId + "/audit-trail", null, null, false),
                404, "RESOURCE_NOT_FOUND");
        // A draft stays hidden from its own staff too: T02 is the public view.
        problem(orgAdmin.browser().send("GET", "/api/campaigns/" + draftId + "/audit-trail", null, null, false),
                404, "RESOURCE_NOT_FOUND");
        problem(anonymous.send("GET", "/api/campaigns/" + UUID.randomUUID() + "/audit-trail", null, null, false),
                404, "RESOURCE_NOT_FOUND");
        status(anonymous.send("GET", "/api/campaigns/" + publishedId + "/audit-trail", null, null, false), 200);

        jdbc.update("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?", ORG_A);
        try {
            problem(anonymous.send("GET", "/api/campaigns/" + publishedId + "/audit-trail", null, null, false),
                    404, "RESOURCE_NOT_FOUND");
        } finally {
            jdbc.update("UPDATE organizations SET status = 'APPROVED' WHERE id = ?", ORG_A);
        }
    }

    @Test
    void publicPagesAreStableAndBounded() throws Exception {
        UUID campaignId = publishedCampaign(actor(ORG_A, "ORG_ADMIN", false));
        UUID caseId = jdbc.queryForObject("SELECT aid_case_id FROM campaigns WHERE id = ?", UUID.class, campaignId);
        Instant at = Instant.now().plus(2, ChronoUnit.HOURS);
        for (int i = 0; i < 3; i++) {
            event(caseId, "AID_PLEDGED", at); // identical timestamps: the id breaks the tie
            event(caseId, "CASE_UPDATED", at); // filtered out, must not shift pages
        }
        List<String> expected = jdbc.queryForList("""
                SELECT id::text FROM audit_events
                WHERE aid_case_id = ? AND event_type IN
                      ('CASE_VERIFIED','CAMPAIGN_PUBLISHED','AID_PLEDGED','GOODS_RECEIVED','DELIVERY_RECORDED')
                ORDER BY occurred_at, id
                """, String.class, caseId);

        Browser anonymous = new Browser();
        List<String> seen = new ArrayList<>();
        for (int page = 0; ; page++) {
            var response = anonymous.send("GET",
                    "/api/campaigns/" + campaignId + "/audit-trail?page=" + page + "&size=2", null, null, false);
            status(response, 200);
            JsonNode body = node(response);
            assertThat(body.path("totalElements").asLong()).isEqualTo(expected.size());
            if (body.path("items").isEmpty()) break;
            body.path("items").forEach(item -> seen.add(item.path("eventId").asText()));
        }
        assertThat(seen).containsExactlyElementsOf(expected);

        String path = "/api/campaigns/" + campaignId + "/audit-trail";
        problem(anonymous.send("GET", path + "?size=101", null, null, false), 400, "VALIDATION_ERROR");
        problem(anonymous.send("GET", path + "?page=-1", null, null, false), 400, "VALIDATION_ERROR");
    }
}
