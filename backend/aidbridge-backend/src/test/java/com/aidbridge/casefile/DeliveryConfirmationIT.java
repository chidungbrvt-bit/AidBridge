package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.shared.idempotency.IdempotencyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.JsonNode;

/** D02 HTTP contract tests. Uses the isolated PostgreSQL schema of AbstractCaseHttpIT. */
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class DeliveryConfirmationIT extends AbstractDeliveryHttpIT {

    @MockitoSpyBean
    IdempotencyService idempotency;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void lateFailureRollsBackConfirmationAuditJobAndJournalThenSameKeyCanRetry() throws Exception {
        assertLateFailureRollsBack("CONFIRMED");
    }

    @Test
    void lateFailureRollsBackReviewDisputeAuditsAndJournalThenSameKeyCanRetry() throws Exception {
        assertLateFailureRollsBack("REQUIRES_REVIEW");
    }

    @Test
    void competingIndependentConfirmersCommitExactlyOneDecisionAndJob() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Actor firstReviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        Actor secondReviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        // Each round uses fresh data. All rounds must pass; failures are never retried until green.
        for (int round = 0; round < 3; round++) {
            Stock stock = stockedCase(orgAdmin);
            UUID deliveryId = record(stock, 4, 1);
            var body = decision("CONFIRMED", stock.evidenceIds(), null);
            String firstKey = newKey();
            String secondKey = newKey();
            var responses = race(List.of(
                    () -> confirm(firstReviewer, deliveryId, body, firstKey),
                    () -> confirm(secondReviewer, deliveryId, body, secondKey)));

            assertThat(responses).as("round %d", round)
                    .extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            int winnerIndex = responses.getFirst().statusCode() == 200 ? 0 : 1;
            var accepted = responses.get(winnerIndex);
            var rejected = responses.get(1 - winnerIndex);
            Actor winner = winnerIndex == 0 ? firstReviewer : secondReviewer;
            Actor loser = winnerIndex == 0 ? secondReviewer : firstReviewer;
            String winningKey = winnerIndex == 0 ? firstKey : secondKey;
            String losingKey = winnerIndex == 0 ? secondKey : firstKey;

            status(accepted, 200);
            problem(rejected, 409, "ALREADY_CONFIRMED");
            JsonNode response = node(accepted);
            assertThat(response.path("reviewerId").asText()).isEqualTo(winner.id().toString());
            assertThat(jdbc.queryForList("SELECT confirmed_by FROM delivery_confirmations WHERE delivery_id = ?",
                    UUID.class, deliveryId)).containsExactly(winner.id());
            assertThat(count("disputes", "aid_case_id", stock.caseId())).isZero();
            assertThat(decisionAuditCount(stock)).isEqualTo(1);
            assertThat(count("blockchain_jobs", "audit_event_id",
                    UUID.fromString(response.path("auditEventId").asText()))).isEqualTo(1);
            assertThat(journalCount(winner, deliveryId, winningKey)).isEqualTo(1);
            assertThat(journalCount(loser, deliveryId, losingKey)).isZero();
            totals(response, stock.rice(), 10, 6, 4, 4, 2);
            totals(response, stock.water(), 4, 2, 1, 1, 1);
            assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");
            assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(1);
            assertThat(count("delivery_items", "delivery_id", deliveryId)).isEqualTo(2);
            assertThat(sequences(stock.caseId())).containsExactly(
                    "CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3",
                    "DELIVERY_RECORDED:4", "DELIVERY_CONFIRMED:5");
        }
    }

    private void assertLateFailureRollsBack(String value) throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 4, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var body = decision(value, stock.evidenceIds(),
                "REQUIRES_REVIEW".equals(value) ? "The evidence needs further review." : null);
        String key = newKey();
        var sequencesBefore = sequences(stock.caseId());
        var observedBeforeFailure = new AtomicReference<List<Long>>();
        IdempotencyService spy = AopTestUtils.getUltimateTargetObject(idempotency);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            // This runs on the HTTP server thread inside ConfirmationService's transaction.
            // Flush proves the writes reached PostgreSQL; none of them have committed yet.
            entityManager.flush();
            observedBeforeFailure.set(List.of(
                    count("delivery_confirmations", "delivery_id", deliveryId),
                    count("disputes", "aid_case_id", stock.caseId()),
                    decisionAuditCount(stock),
                    count("blockchain_jobs", "aid_case_id", stock.caseId()),
                    journalCount(reviewer, deliveryId, key)));
            throw new IllegalStateException("Synthetic failure after flushing D02 transaction");
        }).when(spy).execute(eq(reviewer.id()), eq(ORG_A), eq("POST:" + route(deliveryId)),
                eq(key), any(), eq(ConfirmationResponse.class), any());
        try {
            var failed = confirm(reviewer, deliveryId, body, key);
            problem(failed, 500, "INTERNAL_SERVER_ERROR");
            assertThat(failed.body()).doesNotContain("Synthetic failure");
        } finally {
            reset(spy);
        }

        assertThat(observedBeforeFailure.get()).as("all effects existed inside the transaction before failure")
                .isEqualTo("CONFIRMED".equals(value)
                        ? List.of(1L, 0L, 1L, 5L, 1L)
                        : List.of(1L, 1L, 2L, 4L, 1L));
        assertNoDecision(stock, deliveryId);
        assertThat(journalCount(reviewer, deliveryId, key)).isZero();
        assertThat(sequences(stock.caseId())).isEqualTo(sequencesBefore);
        assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");
        assertThat(count("deliveries", "aid_case_id", stock.caseId())).isEqualTo(1);
        assertThat(count("delivery_items", "delivery_id", deliveryId)).isEqualTo(2);
        assertThat(deliveredQty(stock.rice())).isEqualTo(4);
        assertThat(deliveredQty(stock.water())).isEqualTo(1);

        // A rolled-back request must not consume its key or prevent a genuine retry.
        var retry = confirm(reviewer, deliveryId, body, key);
        status(retry, 200);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        assertThat(journalCount(reviewer, deliveryId, key)).isEqualTo(1);
        boolean confirmed = "CONFIRMED".equals(value);
        assertThat(count("disputes", "aid_case_id", stock.caseId())).isEqualTo(confirmed ? 0 : 1);
        assertThat(decisionAuditCount(stock)).isEqualTo(confirmed ? 1 : 2);
        assertThat(sequences(stock.caseId())).hasSize(confirmed ? 5 : 4);
        totals(node(retry), stock.rice(), 10, 6, 4, confirmed ? 4 : 0, 2);
        totals(node(retry), stock.water(), 4, 2, 1, confirmed ? 1 : 0, 1);
    }

    private long journalCount(Actor actor, UUID deliveryId, String key) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM idempotency_records
                WHERE actor_id = ? AND operation = ? AND key = ?
                """, Long.class, actor.id(), "POST:" + route(deliveryId), key);
    }

    @Test
    void confirmsPartialDeliveryAndReplaysWithoutDuplicatingEffects() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 4, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        String key = newKey();

        var first = confirm(reviewer, deliveryId, body, key);
        status(first, 200);
        JsonNode response = node(first);
        assertThat(response.path("deliveryId").asText()).isEqualTo(deliveryId.toString());
        assertThat(response.path("reviewerId").asText()).isEqualTo(reviewer.id().toString());
        assertThat(response.path("decision").asText()).isEqualTo("CONFIRMED");
        assertThat(response.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");
        assertThat(response.has("disputeId")).isFalse();
        assertThat(Instant.parse(response.path("reviewedAt").asText())).isNotNull();
        totals(response, stock.rice(), 10, 6, 4, 4, 2);
        totals(response, stock.water(), 4, 2, 1, 1, 1);
        assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");

        UUID confirmationId = UUID.fromString(response.path("id").asText());
        UUID eventId = UUID.fromString(response.path("auditEventId").asText());
        assertThat(jdbc.queryForMap("""
                SELECT delivery_id, confirmed_by, decision
                FROM delivery_confirmations WHERE id = ?
                """, confirmationId)).containsEntry("delivery_id", deliveryId)
                .containsEntry("confirmed_by", reviewer.id()).containsEntry("decision", "CONFIRMED");
        assertThat(jdbc.queryForMap("""
                SELECT a.event_type, a.event_key, j.status, j.chain_sequence
                FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ?
                """, eventId)).containsEntry("event_type", "DELIVERY_CONFIRMED")
                .containsEntry("event_key", confirmationId).containsEntry("status", "PENDING")
                .containsEntry("chain_sequence", 5L);

        var replay = confirm(reviewer, deliveryId, body, key);
        status(replay, 200);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(confirm(reviewer, deliveryId, decision("CONFIRMED", stock.evidenceIds(), "Changed"), key),
                409, "IDEMPOTENCY_CONFLICT");
        problem(confirm(reviewer, deliveryId, body, newKey()), 409, "ALREADY_CONFIRMED");
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        assertThat(count("disputes", "aid_case_id", stock.caseId())).isZero();
        assertThat(decisionAuditCount(stock)).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", eventId)).isEqualTo(1);
        assertThat(sequences(stock.caseId())).containsExactly(
                "CASE_VERIFIED:1", "AID_PLEDGED:2", "GOODS_RECEIVED:3",
                "DELIVERY_RECORDED:4", "DELIVERY_CONFIRMED:5");
    }

    @Test
    void requiresReviewOpensOneDisputeKeepsReservationAndReplaysDespiteOpenDispute() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 4, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var body = decision("REQUIRES_REVIEW", stock.evidenceIds(), "The evidence shows fewer packages.");
        String key = newKey();
        var sequenceBefore = sequences(stock.caseId());
        var first = confirm(reviewer, deliveryId, body, key);
        status(first, 200);
        JsonNode response = node(first);
        assertThat(response.path("decision").asText()).isEqualTo("REQUIRES_REVIEW");
        assertThat(response.path("blockchainStatus").asText()).isEqualTo("NOT_APPLICABLE");
        UUID disputeId = UUID.fromString(response.path("disputeId").asText());
        assertThat(jdbc.queryForMap("SELECT delivery_id, opened_by, status, reason FROM disputes WHERE id = ?",
                disputeId)).containsEntry("delivery_id", deliveryId).containsEntry("opened_by", reviewer.id())
                .containsEntry("status", "OPEN").containsEntry("reason", body.get("reason"));
        totals(response, stock.rice(), 10, 6, 4, 0, 2);
        totals(response, stock.water(), 4, 2, 1, 0, 1);
        UUID eventId = UUID.fromString(response.path("auditEventId").asText());
        assertThat(jdbc.queryForObject("SELECT event_type FROM audit_events WHERE id = ?", String.class, eventId))
                .isEqualTo("DISPUTE_OPENED");
        var replay = confirm(reviewer, deliveryId, body, key);
        status(replay, 200);
        assertThat(node(replay)).isEqualTo(response);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(confirm(reviewer, deliveryId, body, newKey()), 409, "DISPUTE_OPEN");
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        assertThat(count("disputes", "aid_case_id", stock.caseId())).isEqualTo(1);
        assertThat(decisionAuditCount(stock)).isEqualTo(2);
        assertThat(sequences(stock.caseId())).isEqualTo(sequenceBefore);
        assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");
    }

    @Test
    void rejectsRecorderEvenWithConfirmerRoleAndSeparatesForbiddenFromNotFound() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 1, 1);
        jdbc.update("INSERT INTO membership_roles (membership_id, role) VALUES (?, 'DELIVERY_CONFIRMER')",
                volunteer.membership());
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        problem(confirm(volunteer, deliveryId, body, newKey()), 403, "SELF_CONFIRMATION");
        problem(confirm(verifier, deliveryId, body, newKey()), 403, "FORBIDDEN");
        problem(confirm(actor(ORG_B, "DELIVERY_CONFIRMER", false), deliveryId, body, newKey()),
                404, "RESOURCE_NOT_FOUND");
        problem(confirm(admin, deliveryId, body, newKey()), 404, "RESOURCE_NOT_FOUND");
        assertNoDecision(stock, deliveryId);
    }

    @Test
    void validatesExactEvidenceSetReasonAndKeyWithoutWritingDecisions() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 1, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        assertThat(stock.evidenceIds()).hasSizeGreaterThan(1);
        var tooMany = new ArrayList<>(stock.evidenceIds());
        tooMany.add(UUID.randomUUID());
        var duplicate = new ArrayList<>(stock.evidenceIds());
        duplicate.add(stock.evidenceIds().getFirst());
        for (var ids : List.of(List.<UUID>of(), List.of(UUID.randomUUID()),
                stock.evidenceIds().subList(0, 1), tooMany, duplicate)) {
            problem(confirm(reviewer, deliveryId, decision("CONFIRMED", ids, null), newKey()),
                    400, "VALIDATION_ERROR");
        }
        for (String reason : List.of("", "   ", "x".repeat(2001))) {
            problem(confirm(reviewer, deliveryId, decision("REQUIRES_REVIEW", stock.evidenceIds(), reason), newKey()),
                    400, "VALIDATION_ERROR");
        }
        problem(confirm(reviewer, deliveryId, decision("REQUIRES_REVIEW", stock.evidenceIds(), null), newKey()),
                400, "VALIDATION_ERROR");
        problem(confirm(reviewer, deliveryId, decision("CONFIRMED", stock.evidenceIds(), null), null),
                400, "VALIDATION_ERROR");
        assertNoDecision(stock, deliveryId);
        // Set matching accepts reordered IDs for a NEW command, not necessarily for an idempotent retry.
        var reversed = new ArrayList<>(stock.evidenceIds());
        Collections.reverse(reversed);
        status(confirm(reviewer, deliveryId, decision("CONFIRMED", reversed, null), newKey()), 200);
    }

    @Test
    void rejectsMissingAndInvalidCsrfWithoutWriting() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 1, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        problem(reviewer.browser().send("POST", route(deliveryId), body, newKey(), false), 403, "CSRF_INVALID");
        String previous = reviewer.browser().token;
        try {
            reviewer.browser().token = "invalid-token";
            problem(confirm(reviewer, deliveryId, body, newKey()), 403, "CSRF_INVALID");
        } finally {
            reviewer.browser().token = previous;
        }
        assertNoDecision(stock, deliveryId);
    }

    @Test
    void replayRechecksRoleMembershipAndOrganization() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 1, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        String key = newKey();
        status(confirm(reviewer, deliveryId, body, key), 200);
        jdbc.update("UPDATE membership_roles SET role = 'VERIFIER' WHERE membership_id = ?", reviewer.membership());
        problem(confirm(reviewer, deliveryId, body, key), 403, "FORBIDDEN");
        jdbc.update("UPDATE membership_roles SET role = 'DELIVERY_CONFIRMER' WHERE membership_id = ?", reviewer.membership());
        jdbc.update("UPDATE organization_memberships SET status = 'DISABLED' WHERE id = ?", reviewer.membership());
        problem(confirm(reviewer, deliveryId, body, key), 404, "RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE organization_memberships SET status = 'ACTIVE' WHERE id = ?", reviewer.membership());
        // PENDING is an existing organization state; restore it to avoid affecting later fixtures.
        String previousStatus = jdbc.queryForObject("SELECT status FROM organizations WHERE id = ?", String.class, ORG_A);
        try {
            jdbc.update("UPDATE organizations SET status = 'PENDING' WHERE id = ?", ORG_A);
            problem(confirm(reviewer, deliveryId, body, key), 404, "RESOURCE_NOT_FOUND");
        } finally {
            jdbc.update("UPDATE organizations SET status = ? WHERE id = ?", previousStatus, ORG_A);
        }
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isEqualTo(1);
        assertThat(decisionAuditCount(stock)).isEqualTo(1);
    }

    @Test
    void rejectsFailedDeliveryAndCaseLevelDispute() throws Exception {
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        var attempt = deliver(volunteer, stock.caseId(), failed("Recipient unavailable"), newKey());
        status(attempt, 201);
        UUID failedId = UUID.fromString(node(attempt).path("id").asText());
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        problem(confirm(reviewer, failedId, body, newKey()), 409, "INVALID_STATE");
        UUID deliveryId = record(stock, 1, 1);
        jdbc.update("INSERT INTO disputes (aid_case_id, opened_by, status, reason) VALUES (?, ?, 'UNDER_REVIEW', 'Synthetic issue')",
                stock.caseId(), reviewer.id());
        problem(confirm(reviewer, deliveryId, body, newKey()), 409, "DISPUTE_OPEN");
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isZero();
        assertThat(count("delivery_confirmations", "delivery_id", failedId)).isZero();
        assertThat(decisionAuditCount(stock)).isZero();
    }

    @Test
    void fulfillsOnlyAfterEveryItemTargetIsConfirmedWithoutClosingCase() throws Exception {
        Actor orgAdmin = actor(ORG_A, "ORG_ADMIN", false);
        Stock stock = stockedCase(orgAdmin);
        // Receive the unreceived remainder through R01, rather than modifying immutable stock facts.
        var lines = jdbc.queryForList("""
                SELECT id, contribution_id, campaign_item_id FROM contribution_items
                WHERE campaign_item_id IN (?, ?)
                """, stock.rice(), stock.water());
        UUID commitmentId = (UUID) lines.getFirst().get("contribution_id");
        var remaining = lines.stream().map(line -> Map.of(
                "contributionItemId", line.get("id"),
                "quantity", stock.rice().equals(line.get("campaign_item_id")) ? 4 : 2)).toList();
        status(orgAdmin.browser().send("POST", "/api/commitments/" + commitmentId + "/receipts",
                Map.of("receivedAt", Instant.now().toString(), "items", remaining), newKey(), true), 201);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        UUID first = record(stock, 10, 3);
        var body = decision("CONFIRMED", stock.evidenceIds(), null);
        status(confirm(reviewer, first, body, newKey()), 200);
        assertThat(campaignStatus(stock)).isEqualTo("PUBLISHED");
        var last = deliver(volunteer, stock.caseId(), delivered(stock.evidenceIds(), stock.water(), 1), newKey());
        status(last, 201);
        UUID lastId = UUID.fromString(node(last).path("id").asText());
        var confirmed = confirm(reviewer, lastId, body, newKey());
        status(confirmed, 200);
        totals(node(confirmed), stock.rice(), 10, 10, 10, 10, 0);
        totals(node(confirmed), stock.water(), 4, 4, 4, 4, 0);
        assertThat(campaignStatus(stock)).isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT closed_at IS NULL FROM campaigns WHERE id = ?", Boolean.class,
                stock.campaignId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM aid_cases WHERE id = ?", String.class, stock.caseId()))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void refusesVoidedDeliveryWithoutCreatingAnotherDecision() throws Exception {
        assertThat(jdbc.queryForObject("SELECT to_regclass('delivery_voids') IS NOT NULL", Boolean.class))
                .as("V18 must create delivery_voids; do not skip the void acceptance check").isTrue();
        Stock stock = stockedCase(actor(ORG_A, "ORG_ADMIN", false));
        UUID deliveryId = record(stock, 1, 1);
        Actor reviewer = actor(ORG_A, "DELIVERY_CONFIRMER", false);
        UUID disputeId = UUID.randomUUID();
        // Fixture represents an already resolved void. It does not test the future resolve endpoint.
        jdbc.update("""
                INSERT INTO disputes (id, aid_case_id, delivery_id, opened_by, status, reason,
                                      resolved_by, resolved_at, resolution_note)
                VALUES (?, ?, ?, ?, 'RESOLVED', 'Synthetic invalid handover', ?, NOW(), 'Void delivery')
                """, disputeId, stock.caseId(), deliveryId, reviewer.id(), admin.id());
        jdbc.update("""
                INSERT INTO delivery_voids (delivery_id, aid_case_id, dispute_id, voided_by, reason)
                VALUES (?, ?, ?, ?, 'Synthetic invalid handover')
                """, deliveryId, stock.caseId(), disputeId, admin.id());
        problem(confirm(reviewer, deliveryId, decision("CONFIRMED", stock.evidenceIds(), null), newKey()),
                409, "INVALID_STATE");
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isZero();
        assertThat(decisionAuditCount(stock)).isZero();
        assertThat(sequences(stock.caseId())).hasSize(4);
    }

    private UUID record(Stock stock, int rice, int water) throws Exception {
        var response = deliver(volunteer, stock.caseId(),
                delivered(stock.evidenceIds(), stock.rice(), rice, stock.water(), water), newKey());
        status(response, 201);
        return UUID.fromString(node(response).path("id").asText());
    }

    private HttpResponse<String> confirm(Actor actor, UUID deliveryId, Object body, String key) throws Exception {
        return actor.browser().send("POST", route(deliveryId), body, key, true);
    }

    private static String route(UUID deliveryId) {
        return "/api/deliveries/" + deliveryId + "/confirm";
    }

    private static Map<String, Object> decision(String value, List<UUID> evidenceIds, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", value);
        body.put("reviewedEvidenceVersionIds", evidenceIds);
        if (reason != null) body.put("reason", reason);
        return body;
    }

    private void totals(JsonNode response, UUID itemId, long pledged, long received,
                        long delivered, long confirmed, long available) {
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode item : response.path("goodsTotals")) {
            if (itemId.toString().equals(item.path("campaignItemId").asText())) matches.add(item);
        }
        assertThat(matches).hasSize(1);
        JsonNode item = matches.getFirst();
        assertThat(item.path("pledgedQty").asLong()).isEqualTo(pledged);
        assertThat(item.path("receivedQty").asLong()).isEqualTo(received);
        assertThat(item.path("deliveredQty").asLong()).isEqualTo(delivered);
        assertThat(item.path("confirmedQty").asLong()).isEqualTo(confirmed);
        assertThat(item.path("availableToDeliverQty").asLong()).isEqualTo(available);
    }

    private String campaignStatus(Stock stock) {
        return jdbc.queryForObject("SELECT status FROM campaigns WHERE id = ?", String.class, stock.campaignId());
    }

    private long decisionAuditCount(Stock stock) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ?
                AND event_type IN ('DELIVERY_CONFIRMED', 'DELIVERY_REVIEW_REQUIRED', 'DISPUTE_OPENED')
                """, Long.class, stock.caseId());
    }

    private void assertNoDecision(Stock stock, UUID deliveryId) {
        assertThat(count("delivery_confirmations", "delivery_id", deliveryId)).isZero();
        assertThat(count("disputes", "aid_case_id", stock.caseId())).isZero();
        assertThat(decisionAuditCount(stock)).isZero();
        assertThat(sequences(stock.caseId())).hasSize(4);
    }
}
