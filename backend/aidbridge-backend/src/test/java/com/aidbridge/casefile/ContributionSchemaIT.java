package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.entity.ContributionItem;
import com.aidbridge.contribution.repository.ContributionItemRepository;
import com.aidbridge.contribution.repository.ContributionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ContributionSchemaIT extends AbstractCampaignHttpIT {
    @Autowired ContributionRepository contributions;
    @Autowired ContributionItemRepository contributionItems;
    @Autowired CampaignRepository campaigns;
    @Autowired CampaignItemRepository campaignItems;
    @Autowired PlatformTransactionManager transactionManager;

    // ---------------------------------------------------------------- D52 donor grant

    @Test
    void donorFlagDefaultsToFalseAndMeReflectsTheExplicitGrant() throws Exception {
        Actor account = actor(null, null, false);
        assertThat(jdbc.queryForObject("SELECT donor_enabled FROM users WHERE id = ?", Boolean.class, account.id()))
                .isFalse();

        var before = account.browser().send("GET", "/api/auth/me", null, null, false);
        status(before, 200);
        assertThat(node(before).has("donorEnabled")).isTrue();
        assertThat(node(before).path("donorEnabled").asBoolean()).isFalse();

        jdbc.update("UPDATE users SET donor_enabled = TRUE WHERE id = ?", account.id());
        var after = account.browser().send("GET", "/api/auth/me", null, null, false);
        status(after, 200);
        assertThat(node(after).path("donorEnabled").asBoolean()).isTrue();
    }

    // ---------------------------------------------------------------- V11 constraints

    @Test
    void acceptsValidContributionWithV11Defaults() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst();

        UUID contributionId = insertContribution(campaignId, ORG_A, donor.id());
        UUID lineId = insertLine(contributionId, campaignId, ORG_A, food, 2, 0);

        assertThat(jdbc.queryForMap("""
                SELECT status, version, octet_length(public_ref) AS ref_len FROM contributions WHERE id = ?
                """, contributionId))
                .containsEntry("status", "PLEDGED")
                .containsEntry("version", 0L)
                .containsEntry("ref_len", 32);
        assertThat(jdbc.queryForObject("SELECT cancelled_quantity FROM contribution_items WHERE id = ?",
                Integer.class, lineId)).isZero();
    }

    @Test
    void rejectsLineQuantitiesOutsideTheAllowedBounds() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst();
        UUID contributionId = insertContribution(campaignId, ORG_A, donor(true).id());

        assertRejected(() -> insertLine(contributionId, campaignId, ORG_A, food, 0, 0));
        assertRejected(() -> insertLine(contributionId, campaignId, ORG_A, food, 1_000_001, 0));
        assertRejected(() -> insertLine(contributionId, campaignId, ORG_A, food, 3, 4));
        assertRejected(() -> insertLine(contributionId, campaignId, ORG_A, food, 3, -1));
        assertThat(count("contribution_items", "contribution_id", contributionId)).isZero();
    }

    @Test
    void rejectsRowsThatCrossCampaignOrOrganizationBoundaries() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignA = publishedCampaign(orgAdmin);
        UUID campaignB = publishedCampaign(orgAdmin);
        UUID itemA = campaignItemIds(campaignA).getFirst();
        UUID itemB = campaignItemIds(campaignB).getFirst();
        UUID contributionA = insertContribution(campaignA, ORG_A, donor.id());

        // Line claims campaign A but points at an item of campaign B.
        assertRejected(() -> insertLine(contributionA, campaignA, ORG_A, itemB, 1, 0));
        // Line is consistent with campaign B but hangs under a contribution of campaign A.
        assertRejected(() -> insertLine(contributionA, campaignB, ORG_A, itemB, 1, 0));
        // Line carries an organization that does not own its campaign.
        assertRejected(() -> insertLine(contributionA, campaignA, ORG_B, itemA, 1, 0));
        // Header carries an organization that does not own its campaign.
        assertRejected(() -> insertContribution(campaignA, ORG_B, donor.id()));

        assertThat(count("contribution_items", "contribution_id", contributionA)).isZero();
        assertThat(count("contributions", "campaign_id", campaignA)).isEqualTo(1);
    }

    @Test
    void publicRefMustBePresentUniqueThirtyTwoBytesAndNonZero() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID donorId = donor(true).id();
        String insert = """
                INSERT INTO contributions (id, campaign_id, donor_user_id, organization_id, public_ref)
                VALUES (?, ?, ?, ?, decode(?, 'hex'))
                """;

        assertRejected(() -> jdbc.update(insert, UUID.randomUUID(), campaignId, donorId, ORG_A, "00".repeat(32)));
        assertRejected(() -> jdbc.update(insert, UUID.randomUUID(), campaignId, donorId, ORG_A, "ab".repeat(31)));
        assertRejected(() -> jdbc.update("""
                INSERT INTO contributions (id, campaign_id, donor_user_id, organization_id, public_ref)
                VALUES (?, ?, ?, ?, NULL)
                """, UUID.randomUUID(), campaignId, donorId, ORG_A));

        String ref = "cd".repeat(32);
        jdbc.update(insert, UUID.randomUUID(), campaignId, donorId, ORG_A, ref);
        assertRejected(() -> jdbc.update(insert, UUID.randomUUID(), campaignId, donorId, ORG_A, ref));
    }

    // ---------------------------------------------------------------- repository queries

    @Test
    void activeTotalsSumPledgedMinusCancelledPerCampaignItem() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID food = items.get(0), hygiene = items.get(1);
        UUID untouched = campaignItemIds(publishedCampaign(orgAdmin)).getFirst();

        UUID first = insertContribution(campaignId, ORG_A, donor(true).id());
        insertLine(first, campaignId, ORG_A, food, 2, 1);
        insertLine(first, campaignId, ORG_A, hygiene, 2, 2);
        UUID second = insertContribution(campaignId, ORG_A, donor(true).id());
        insertLine(second, campaignId, ORG_A, food, 1, 0);

        Map<UUID, Long> totals = toMap(contributionItems.activeTotals(List.of(food, hygiene, untouched)));
        assertThat(totals)
                .containsEntry(food, 2L)       // (2 - 1) + (1 - 0)
                .containsEntry(hygiene, 0L)    // fully cancelled still reports 0, not absent
                .doesNotContainKey(untouched); // no rows at all -> absent, caller defaults to 0
    }

    @Test
    void receivedTotalsSumReceiptLinesPerContributionItem() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID campaignId = publishedCampaign(orgAdmin);
        List<UUID> items = campaignItemIds(campaignId);
        UUID contributionId = insertContribution(campaignId, ORG_A, donor(true).id());
        UUID foodLine = insertLine(contributionId, campaignId, ORG_A, items.get(0), 2, 0);
        UUID hygieneLine = insertLine(contributionId, campaignId, ORG_A, items.get(1), 2, 0);

        // Receipts belong to B08; seeded directly so the B07 cancel rule can be tested later.
        insertReceiptLine(insertReceipt(contributionId, orgAdmin.id()), foodLine, 1);
        insertReceiptLine(insertReceipt(contributionId, orgAdmin.id()), foodLine, 1);

        Map<UUID, Long> totals = toMap(contributionItems.receivedTotals(List.of(foodLine, hygieneLine)));
        assertThat(totals).containsEntry(foodLine, 2L).doesNotContainKey(hygieneLine);
    }

    // ---------------------------------------------------------------- JPA mapping

    @Test
    void jpaEntitiesPersistIntoTheV11Schema() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor donor = donor(true);
        UUID campaignId = publishedCampaign(orgAdmin);
        UUID food = campaignItemIds(campaignId).getFirst();

        UUID contributionId = new TransactionTemplate(transactionManager).execute(tx -> {
            Campaign campaign = campaigns.findById(campaignId).orElseThrow();
            CampaignItem item = campaignItems.findById(food).orElseThrow();
            Contribution contribution = contributions.saveAndFlush(new Contribution(campaign, donor.id()));
            contributionItems.saveAndFlush(new ContributionItem(contribution, item, 2));
            return contribution.getId();
        });

        assertThat(jdbc.queryForMap("""
                SELECT organization_id, donor_user_id, status, octet_length(public_ref) AS ref_len
                FROM contributions WHERE id = ?
                """, contributionId))
                .containsEntry("organization_id", ORG_A)
                .containsEntry("donor_user_id", donor.id())
                .containsEntry("status", "PLEDGED")
                .containsEntry("ref_len", 32);
        assertThat(jdbc.queryForMap("""
                SELECT organization_id, campaign_id, campaign_item_id, pledged_quantity, cancelled_quantity
                FROM contribution_items WHERE contribution_id = ?
                """, contributionId))
                .containsEntry("organization_id", ORG_A)
                .containsEntry("campaign_id", campaignId)
                .containsEntry("campaign_item_id", food)
                .containsEntry("pledged_quantity", 2)
                .containsEntry("cancelled_quantity", 0);
    }

    @Test
    void contributionItemRefusesACampaignItemFromAnotherCampaign() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        UUID donorId = donor(true).id();
        UUID campaignA = publishedCampaign(orgAdmin);
        UUID itemOfB = campaignItemIds(publishedCampaign(orgAdmin)).getFirst();

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Contribution contribution = new Contribution(campaigns.findById(campaignA).orElseThrow(), donorId);
            CampaignItem foreignItem = campaignItems.findById(itemOfB).orElseThrow();
            assertThatThrownBy(() -> new ContributionItem(contribution, foreignItem, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            tx.setRollbackOnly();
        });
    }

    // ---------------------------------------------------------------- helpers

    private UUID insertContribution(UUID campaignId, UUID organizationId, UUID donorId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO contributions (id, campaign_id, donor_user_id, organization_id) VALUES (?, ?, ?, ?)",
                id, campaignId, donorId, organizationId);
        return id;
    }

    private UUID insertLine(UUID contributionId, UUID campaignId, UUID organizationId, UUID campaignItemId,
                            int pledged, int cancelled) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contribution_items
                    (id, contribution_id, campaign_item_id, pledged_quantity, cancelled_quantity, organization_id, campaign_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, contributionId, campaignItemId, pledged, cancelled, organizationId, campaignId);
        return id;
    }

    private UUID insertReceipt(UUID contributionId, UUID recorderId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO goods_receipts (id, contribution_id, received_by, idempotency_key) VALUES (?, ?, ?, ?)",
                id, contributionId, recorderId, UUID.randomUUID());
        return id;
    }

    private void insertReceiptLine(UUID receiptId, UUID contributionItemId, int quantity) {
        jdbc.update("""
                INSERT INTO goods_receipt_items (id, goods_receipt_id, contribution_item_id, received_quantity)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), receiptId, contributionItemId, quantity);
    }

    private static void assertRejected(ThrowingCallable statement) {
        assertThatThrownBy(statement).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Map<UUID, Long> toMap(List<ContributionItemRepository.ItemQuantity> rows) {
        return rows.stream().collect(Collectors.toMap(
                ContributionItemRepository.ItemQuantity::getItemId,
                ContributionItemRepository.ItemQuantity::getQuantity));
    }
}