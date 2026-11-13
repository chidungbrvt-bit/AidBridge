package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class ReceiptSchemaIT {
    private JdbcTemplate jdbc;
    private Flyway flyway;

    @BeforeEach
    void migrateToPreviousVersion() {
        String url = System.getenv("AIDBRIDGE_TEST_DATABASE_URL");
        String user = System.getenv("AIDBRIDGE_TEST_DATABASE_USERNAME");
        String password = System.getenv("AIDBRIDGE_TEST_DATABASE_PASSWORD");
        assertThat(url).isIn("jdbc:postgresql://localhost:5432/aidbridge_test",
                "jdbc:postgresql://127.0.0.1:5432/aidbridge_test");
        assertThat(user).isEqualTo("aidbridge_app");
        String schema = "b08_migration_" + UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema)
                .createSchemas(true).cleanDisabled(true);
        config.target("13").load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password));
        flyway = config.target("14").load();
    }

    @Test
    void upgradePreservesLegacyReceiptAndEnforcesOwnershipAndImmutability() {
        var legacy = seedLegacyReceipt(false, 1);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(jdbc.queryForMap("""
                SELECT r.idempotency_key, r.received_by, r.receipt_note,
                       EXTRACT(EPOCH FROM r.received_at)::bigint AS received_epoch,
                       EXTRACT(EPOCH FROM r.created_at)::bigint AS recorded_epoch,
                       i.contribution_id, i.contribution_item_id, i.received_quantity
                FROM goods_receipts r JOIN goods_receipt_items i ON i.goods_receipt_id = r.id
                WHERE r.id = ?
                """, legacy.receiptId())).containsEntry("idempotency_key", legacy.key())
                .containsEntry("received_by", legacy.actorId()).containsEntry("receipt_note", "Legacy partial receipt")
                .containsEntry("received_epoch", Instant.parse("2026-11-09T01:39:00Z").getEpochSecond())
                .containsEntry("recorded_epoch", Instant.parse("2026-11-09T01:40:00Z").getEpochSecond())
                .containsEntry("contribution_id", legacy.contributionId())
                .containsEntry("contribution_item_id", legacy.lineId()).containsEntry("received_quantity", 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE goods_receipts SET receipt_note = 'Changed' WHERE id = ?",
                legacy.receiptId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM goods_receipt_items WHERE goods_receipt_id = ?",
                legacy.receiptId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO goods_receipt_items
                    (goods_receipt_id, contribution_id, contribution_item_id, received_quantity)
                VALUES (?, ?, ?, 1)
                """, legacy.receiptId(), legacy.contributionId(), legacy.otherLineId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_receipt_item_contribution_commitment");
        assertThat(jdbc.update("INSERT INTO goods_receipts (contribution_id, received_by) VALUES (?, ?)",
                legacy.contributionId(), legacy.actorId())).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
        "true, 1, Legacy receipt lines must belong to the receipt commitment",
        "false, 3, Legacy receipts exceed the active commitment quantity"
    })
    void upgradeRejectsInvalidLegacyDataWithoutChangingIt(boolean wrongCommitment, int quantity, String error) {
        var legacy = seedLegacyReceipt(wrongCommitment, quantity);
        assertThatThrownBy(flyway::migrate).hasMessageContaining(error);
        assertThat(jdbc.queryForObject("""
                SELECT received_quantity FROM goods_receipt_items WHERE goods_receipt_id = ?
                """, Integer.class, legacy.receiptId())).isEqualTo(quantity);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
    }

    private Legacy seedLegacyReceipt(boolean wrongCommitment, int quantity) {
        UUID orgId = jdbc.queryForObject(
                "INSERT INTO organizations (name, status) VALUES ('Receipt fixture org', 'APPROVED') RETURNING id",
                UUID.class);
        UUID actor = jdbc.queryForObject("""
                INSERT INTO users (full_name, email, password_hash)
                VALUES ('Receipt fixture staff', 'receipt-fixture@example.com', 'unused-fixture-hash') RETURNING id
                """, UUID.class);
        UUID beneficiary = jdbc.queryForObject("""
                INSERT INTO beneficiaries (organization_id, reference_code, display_label, created_by)
                VALUES (?, 'FIXTURE', 'Demo household', ?) RETURNING id
                """, UUID.class, orgId, actor);
        UUID caseId = jdbc.queryForObject("""
                INSERT INTO aid_cases (case_code, organization_id, beneficiary_id, created_by,
                    title, public_description, internal_title)
                VALUES ('CASE-FIXTURE', ?, ?, ?, 'Receipt fixture', '', 'Receipt fixture') RETURNING id
                """, UUID.class, orgId, beneficiary, actor);
        UUID need = jdbc.queryForObject("""
                INSERT INTO case_need_items (aid_case_id, item_name, requested_quantity)
                VALUES (?, 'Rice', 4) RETURNING id
                """, UUID.class, caseId);
        UUID campaign = jdbc.queryForObject("""
                INSERT INTO campaigns (aid_case_id, organization_id, created_by, title, public_summary)
                VALUES (?, ?, ?, 'Receipt fixture', 'Simulated goods') RETURNING id
                """, UUID.class, caseId, orgId, actor);
        UUID item = jdbc.queryForObject("""
                INSERT INTO campaign_items (campaign_id, case_need_item_id, organization_id,
                    item_name, unit, target_quantity)
                VALUES (?, ?, ?, 'Rice', 'PACKAGE', 4) RETURNING id
                """, UUID.class, campaign, need, orgId);
        UUID contribution = insertContribution(campaign, orgId, actor);
        UUID otherContribution = insertContribution(campaign, orgId, actor);
        UUID line = insertLine(contribution, campaign, orgId, item);
        UUID otherLine = insertLine(otherContribution, campaign, orgId, item);
        UUID key = UUID.randomUUID();
        UUID receipt = jdbc.queryForObject("""
                INSERT INTO goods_receipts (contribution_id, received_by, idempotency_key,
                    receipt_note, received_at, created_at)
                VALUES (?, ?, ?, 'Legacy partial receipt', '2026-11-09T01:39:00Z', '2026-11-09T01:40:00Z')
                RETURNING id
                """, UUID.class, contribution, actor, key);
        jdbc.update("""
                INSERT INTO goods_receipt_items (goods_receipt_id, contribution_item_id, received_quantity)
                VALUES (?, ?, ?)
                """, receipt, wrongCommitment ? otherLine : line, quantity);
        return new Legacy(receipt, contribution, line, otherLine, actor, key);
    }

    private UUID insertContribution(UUID campaign, UUID orgId, UUID actor) {
        return jdbc.queryForObject("""
                INSERT INTO contributions (campaign_id, organization_id, donor_user_id)
                VALUES (?, ?, ?) RETURNING id
                """, UUID.class, campaign, orgId, actor);
    }

    private UUID insertLine(UUID contribution, UUID campaign, UUID orgId, UUID item) {
        return jdbc.queryForObject("""
                INSERT INTO contribution_items (contribution_id, campaign_id, organization_id,
                    campaign_item_id, pledged_quantity)
                VALUES (?, ?, ?, ?, 2) RETURNING id
                """, UUID.class, contribution, campaign, orgId, item);
    }

    private record Legacy(UUID receiptId, UUID contributionId, UUID lineId, UUID otherLineId,
                          UUID actorId, UUID key) {
    }
}
