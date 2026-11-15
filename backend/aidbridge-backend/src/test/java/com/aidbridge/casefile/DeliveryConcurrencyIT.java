package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** A failure here, even an occasional one, is a real bug: never retry until green or loosen assertions. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DeliveryConcurrencyIT extends AbstractDeliveryHttpIT {

    @Test
    void twoDeliveriesThatEachFitButTogetherExceedStockCommitOnlyOne() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor secondVolunteer = actor(ORG_A, "VOLUNTEER", false);
        for (int round = 0; round < 3; round++) {
            Stock stock = stockedCase(orgAdmin);
            // Available Rice 6 / Water 2. Each request (Rice 4 + Water 1) fits alone; Rice 4 + 4 = 8 > 6.
            var first = delivered(stock.evidenceIds(), stock.rice(), 4, stock.water(), 1);
            var second = delivered(stock.evidenceIds(), stock.rice(), 4, stock.water(), 1);

            var responses = race(List.of(
                    () -> deliver(volunteer, stock.caseId(), first, newKey()),
                    () -> deliver(secondVolunteer, stock.caseId(), second, newKey())));

            assertThat(responses).extracting(HttpResponse::statusCode).as("round %d", round)
                    .containsExactlyInAnyOrder(201, 409);
            var accepted = responses.stream().filter(r -> r.statusCode() == 201).findFirst().orElseThrow();
            var rejected = responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
            status(accepted, 201);
            problem(rejected, 409, "QUANTITY_EXCEEDED");
            UUID deliveryId = UUID.fromString(node(accepted).path("id").asText());

            // Every persisted effect belongs to the accepted write only.
            assertThat(jdbc.queryForList("SELECT id FROM deliveries WHERE aid_case_id = ?",
                    UUID.class, stock.caseId())).as("round %d", round).containsExactly(deliveryId);
            assertThat(jdbc.queryForList("SELECT DISTINCT delivery_id FROM delivery_items WHERE campaign_id = ?",
                    UUID.class, stock.campaignId())).containsExactly(deliveryId);
            assertThat(jdbc.queryForList("SELECT DISTINCT delivery_id FROM delivery_evidence_links WHERE aid_case_id = ?",
                    UUID.class, stock.caseId())).containsExactly(deliveryId);
            assertThat(deliveredQty(stock.rice())).isEqualTo(4);
            // The rejected request's Water line fitted on its own, but it must roll back with the Rice line.
            assertThat(deliveredQty(stock.water())).isEqualTo(1);
            assertThat(jdbc.queryForList("""
                    SELECT entity_id FROM audit_events
                    WHERE aid_case_id = ? AND event_type IN ('DELIVERY_RECORDED', 'DELIVERY_ATTEMPT_FAILED')
                    """, UUID.class, stock.caseId())).containsExactly(deliveryId);
            assertThat(sequences(stock.caseId())).containsExactly(
                    "CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3", "DELIVERY_RECORDED:4");
        }
    }
}