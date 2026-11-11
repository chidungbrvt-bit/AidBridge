package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.aidbridge.verification.service.VerificationOutboxService;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class VerificationApiIT extends AbstractCaseHttpIT {
    @MockitoSpyBean VerificationOutboxService outbox;

    @Test
    void independentReviewerApprovesExactRevisionAndReplayKeepsOneApproval() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long reviewedVersion = submitted.path("version").asLong();
        String key = newKey();
        var request = request(reviewedVersion, evidenceIds(submitted));

        var first = approve(verifier, caseId, request, key);
        status(first, 200);
        var decision = node(first);
        UUID verificationId = UUID.fromString(decision.path("verificationId").asText());
        UUID eventId = UUID.fromString(decision.path("auditEventId").asText());
        assertThat(decision.path("caseId").asText()).isEqualTo(caseId.toString());
        assertThat(decision.path("reviewerId").asText()).isEqualTo(verifier.id().toString());
        assertThat(decision.path("decision").asText()).isEqualTo("APPROVED");
        assertThat(decision.path("caseStatus").asText()).isEqualTo("VERIFIED");
        assertThat(decision.path("caseVersion").asLong()).isEqualTo(reviewedVersion + 1);
        assertThat(decision.path("reviewedAt").asText()).isNotBlank();
        assertThat(decision.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");

        var retry = approve(verifier, caseId, request, key);
        status(retry, 200);
        assertThat(node(retry)).isEqualTo(decision);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(approve(verifier, caseId,
                requestWithReason(reviewedVersion, evidenceIds(submitted), "Different approval payload"), key),
                409, "IDEMPOTENCY_CONFLICT");

        assertThat(jdbc.queryForMap("""
                SELECT status, version FROM aid_cases WHERE id = ?
                """, caseId)).containsEntry("status", "VERIFIED")
                .containsEntry("version", reviewedVersion + 1);
        assertThat(jdbc.queryForMap("""
                SELECT v.decision, v.verifier_id, r.case_version
                FROM verifications v
                JOIN case_revisions r
                  ON r.aid_case_id = v.aid_case_id AND r.case_version = v.reviewed_case_version
                WHERE v.id = ? AND v.aid_case_id = ?
                """, verificationId, caseId)).containsEntry("decision", "APPROVED")
                .containsEntry("verifier_id", verifier.id())
                .containsEntry("case_version", reviewedVersion);
        assertThat(jdbc.queryForMap("""
                SELECT a.event_type, a.event_key, j.status
                FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ? AND a.aid_case_id = ?
                """, eventId, caseId)).containsEntry("event_type", "CASE_VERIFIED")
                .containsEntry("event_key", verificationId)
                .containsEntry("status", "PENDING");
        assertApprovalCounts(caseId, 1);
        assertThat(count("idempotency_records", "actor_id", verifier.id())).isEqualTo(1);
    }

    @Test
    void creatorWithVerifierRoleAndVerifierFromAnotherOrganizationCannotApprove() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        var request = request(submitted.path("version").asLong(), evidenceIds(submitted));

        problem(approve(volunteer, caseId, request, newKey()), 403, "FORBIDDEN");
        jdbc.update("INSERT INTO membership_roles (membership_id, role) VALUES (?, 'VERIFIER')",
                volunteer.membership());
        problem(approve(volunteer, caseId, request, newKey()), 403, "FORBIDDEN");

        var foreignVerifier = actor(ORG_B, "VERIFIER", false);
        problem(approve(foreignVerifier, caseId, request, newKey()), 404, "RESOURCE_NOT_FOUND");
        assertStillSubmitted(submitted);
    }

    @Test
    void rejectsStaleVersionAndIncompleteDuplicateOrForeignEvidence() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long version = submitted.path("version").asLong();
        var ids = evidenceIds(submitted);
        String otherCaseEvidence = evidenceIds(create()).getFirst();

        problem(approve(verifier, caseId, request(version + 1, ids), newKey()), 409, "VERSION_CONFLICT");
        problem(approve(verifier, caseId, request(version, List.of(ids.getFirst())), newKey()),
                400, "VALIDATION_ERROR");
        problem(approve(verifier, caseId, request(version, List.of(ids.getFirst(), ids.getFirst())), newKey()),
                400, "VALIDATION_ERROR");
        problem(approve(verifier, caseId, request(version, List.of(ids.getFirst(), otherCaseEvidence)), newKey()),
                400, "VALIDATION_ERROR");
        assertStillSubmitted(submitted);
    }

    @Test
    void revokedMembershipCannotReplayEarlierApproval() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        var request = request(submitted.path("version").asLong(), evidenceIds(submitted));
        String key = newKey();
        status(approve(verifier, caseId, request, key), 200);

        jdbc.update("UPDATE organization_memberships SET status = 'DISABLED' WHERE id = ?", verifier.membership());
        problem(approve(verifier, caseId, request, key), 404, "RESOURCE_NOT_FOUND");
        assertApprovalCounts(caseId, 1);
    }

    @Test
    void competingReviewersWithDifferentKeysCommitOnlyOneApproval() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        var request = request(submitted.path("version").asLong(), evidenceIds(submitted));
        var secondVerifier = actor(ORG_A, "VERIFIER", false);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return approve(verifier, caseId, request, newKey());
            });
            var second = executor.submit(() -> {
                start.await();
                return approve(secondVerifier, caseId, request, newKey());
            });
            start.countDown();

            var responses = List.of(first.get(35, TimeUnit.SECONDS), second.get(35, TimeUnit.SECONDS));
            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            for (var response : responses) {
                if (response.statusCode() == 409) {
                    problem(response, 409, "VERSION_CONFLICT");
                } else {
                    status(response, 200);
                }
            }
        }
        assertApprovalCounts(caseId, 1);
    }

    @Test
    void failureAfterOutboxWriteRollsBackCaseDecisionAuditJobAndRequestKey() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        var request = request(submitted.path("version").asLong(), evidenceIds(submitted));
        var writtenEvent = new AtomicReference<UUID>();
        VerificationOutboxService outboxSpy = AopTestUtils.getUltimateTargetObject(outbox);
        doAnswer(invocation -> {
            writtenEvent.set((UUID) invocation.callRealMethod());
            throw new IllegalStateException("Synthetic failure after writing verification outbox");
        }).when(outboxSpy).enqueueApprovedVerification(eq(ORG_A), any(UUID.class));
        try {
            problem(approve(verifier, caseId, request, newKey()), 500, "INTERNAL_SERVER_ERROR");
        } finally {
            org.mockito.Mockito.reset(outboxSpy);
        }
        assertThat(writtenEvent.get()).isNotNull();
        assertStillSubmitted(submitted);
        assertThat(count("idempotency_records", "actor_id", verifier.id())).isZero();
    }

    @Test
    void independentReviewerRejectsCaseWithRequiredReasonAndBlocksSubsequentWrites() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long reviewedVersion = submitted.path("version").asLong();
        var ids = evidenceIds(submitted);
        String reason = "Household moved outside coverage area";
        String key = newKey();
        var body = requestWithReason(reviewedVersion, ids, reason);

        var first = reject(verifier, caseId, body, key);
        status(first, 200);
        var decision = node(first);
        UUID verificationId = UUID.fromString(decision.path("verificationId").asText());
        UUID eventId = UUID.fromString(decision.path("auditEventId").asText());
        assertThat(decision.path("caseId").asText()).isEqualTo(caseId.toString());
        assertThat(decision.path("reviewerId").asText()).isEqualTo(verifier.id().toString());
        assertThat(decision.path("decision").asText()).isEqualTo("REJECTED");
        assertThat(decision.path("caseStatus").asText()).isEqualTo("REJECTED");
        assertThat(decision.path("caseVersion").asLong()).isEqualTo(reviewedVersion + 1);
        assertThat(decision.path("reviewedAt").asText()).isNotBlank();
        assertThat(decision.path("blockchainStatus").asText()).isEqualTo("NOT_APPLICABLE");

        var retry = reject(verifier, caseId, body, key);
        status(retry, 200);
        assertThat(node(retry)).isEqualTo(decision);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("true");
        problem(reject(verifier, caseId, requestWithReason(reviewedVersion, ids, "Changed reason"), key),
                409, "IDEMPOTENCY_CONFLICT");

        assertThat(jdbc.queryForMap("SELECT status, version FROM aid_cases WHERE id = ?", caseId))
                .containsEntry("status", "REJECTED")
                .containsEntry("version", reviewedVersion + 1);
        assertThat(jdbc.queryForMap("""
                SELECT decision, verifier_id, reviewed_case_version, notes
                FROM verifications WHERE id = ? AND aid_case_id = ?
                """, verificationId, caseId))
                .containsEntry("decision", "REJECTED")
                .containsEntry("verifier_id", verifier.id())
                .containsEntry("reviewed_case_version", reviewedVersion)
                .containsEntry("notes", reason);
        assertThat(jdbc.queryForMap("""
                SELECT event_type, entity_type, entity_id, event_key
                FROM audit_events WHERE id = ? AND aid_case_id = ?
                """, eventId, caseId))
                .containsEntry("event_type", "CASE_REJECTED")
                .containsEntry("entity_type", "VERIFICATION")
                .containsEntry("entity_id", verificationId)
                .containsEntry("event_key", verificationId);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE a.aid_case_id = ?
                """, Long.class, caseId)).isZero();

        long nextVersion = reviewedVersion + 1;
        problem(volunteer.browser().send("PATCH", "/api/cases/" + caseId,
                Map.of("expectedVersion", nextVersion, "internalTitle", "Edit after reject"), newKey(), true),
                409, "INVALID_STATE");
        problem(volunteer.browser().send("POST", "/api/cases/" + caseId + "/resubmit",
                Map.of("expectedVersion", nextVersion), newKey(), true),
                409, "INVALID_STATE");
        problem(approve(verifier, caseId, request(nextVersion, ids), newKey()), 409, "INVALID_STATE");
        problem(requestChanges(verifier, caseId, requestWithReason(nextVersion, ids, "Follow up"), newKey()),
                409, "INVALID_STATE");
    }

    @Test
    void rejectAndRequestChangesRequireValidReasonAndEnforceIndependentVerifier() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long version = submitted.path("version").asLong();
        var ids = evidenceIds(submitted);

        for (String path : List.of("/reject", "/request-changes")) {
            problem(verifier.browser().send("POST", "/api/cases/" + caseId + path,
                    request(version, ids), newKey(), true), 400, "VALIDATION_ERROR");
            problem(verifier.browser().send("POST", "/api/cases/" + caseId + path,
                    requestWithReason(version, ids, "   "), newKey(), true), 400, "VALIDATION_ERROR");
            problem(verifier.browser().send("POST", "/api/cases/" + caseId + path,
                    requestWithReason(version, ids, "Unsafe <script> marker"), newKey(), true), 400, "VALIDATION_ERROR");
            problem(verifier.browser().send("POST", "/api/cases/" + caseId + path,
                    requestWithReason(version, ids, "External https://example.com link"), newKey(), true),
                    400, "VALIDATION_ERROR");
        }

        var validReason = requestWithReason(version, ids, "Independent verifier check");
        problem(reject(volunteer, caseId, validReason, newKey()), 403, "FORBIDDEN");
        problem(requestChanges(volunteer, caseId, validReason, newKey()), 403, "FORBIDDEN");

        jdbc.update("INSERT INTO membership_roles (membership_id, role) VALUES (?, 'VERIFIER')",
                volunteer.membership());
        problem(reject(volunteer, caseId, validReason, newKey()), 403, "FORBIDDEN");
        problem(requestChanges(volunteer, caseId, validReason, newKey()), 403, "FORBIDDEN");

        var foreignVerifier = actor(ORG_B, "VERIFIER", false);
        problem(reject(foreignVerifier, caseId, validReason, newKey()), 404, "RESOURCE_NOT_FOUND");
        problem(requestChanges(foreignVerifier, caseId, validReason, newKey()), 404, "RESOURCE_NOT_FOUND");

        assertStillSubmitted(submitted);
    }

    @Test
    void requestChangesFollowedByCreatorRevisionResubmissionAndIndependentApproval() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long initialVersion = submitted.path("version").asLong();
        var initialEvidence = evidenceIds(submitted);
        String reason = "Clarify household size and attach updated note";
        String requestChangesKey = newKey();
        var requestChangesBody = requestWithReason(initialVersion, initialEvidence, reason);

        var firstReview = requestChanges(verifier, caseId, requestChangesBody, requestChangesKey);
        status(firstReview, 200);
        var changesDecision = node(firstReview);
        UUID firstVerificationId = UUID.fromString(changesDecision.path("verificationId").asText());
        assertThat(changesDecision.path("decision").asText()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changesDecision.path("caseStatus").asText()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changesDecision.path("caseVersion").asLong()).isEqualTo(initialVersion + 1);
        assertThat(changesDecision.path("blockchainStatus").asText()).isEqualTo("NOT_APPLICABLE");

        var replayChanges = requestChanges(verifier, caseId, requestChangesBody, requestChangesKey);
        status(replayChanges, 200);
        assertThat(node(replayChanges)).isEqualTo(changesDecision);
        assertThat(replayChanges.headers().firstValue("Idempotent-Replayed")).contains("true");

        // Resubmit without a post-review revision or approving while in CHANGES_REQUESTED must fail.
        problem(volunteer.browser().send("POST", "/api/cases/" + caseId + "/resubmit",
                Map.of("expectedVersion", initialVersion + 1), newKey(), true), 409, "INVALID_STATE");
        problem(approve(verifier, caseId, request(initialVersion + 1, initialEvidence), newKey()),
                409, "INVALID_STATE");

        // Creator updates case details and supersedes the first evidence version.
        var patched = volunteer.browser().send("PATCH", "/api/cases/" + caseId,
                Map.of("expectedVersion", initialVersion + 1, "internalTitle", "Clarified household case"),
                newKey(), true);
        status(patched, 200);
        long patchedVersion = node(patched).path("version").asLong();

        var firstEvidenceNode = submitted.path("evidenceVersions").get(0);
        var appendedEvidence = volunteer.browser().send("POST", "/api/cases/" + caseId + "/evidence-versions",
                Map.of(
                        "expectedCaseVersion", patchedVersion,
                        "evidenceFileId", firstEvidenceNode.path("evidenceFileId").asText(),
                        "supersedesVersionId", firstEvidenceNode.path("id").asText(),
                        "fixtureKey", "household-note-v2"
                ),
                newKey(), true);
        status(appendedEvidence, 201);

        var resubmitted = volunteer.browser().send("POST", "/api/cases/" + caseId + "/resubmit",
                Map.of("expectedVersion", patchedVersion + 1), newKey(), true);
        status(resubmitted, 200);
        var resubmittedNode = node(resubmitted);
        long resubmittedVersion = resubmittedNode.path("version").asLong();
        assertThat(resubmittedNode.path("status").asText()).isEqualTo("SUBMITTED");

        // Stale evidence set (using superseded version) is rejected; current evidence set succeeds.
        problem(approve(verifier, caseId, request(resubmittedVersion, initialEvidence), newKey()),
                400, "VALIDATION_ERROR");

        var currentEvidence = evidenceIds(resubmittedNode);
        var approved = approve(verifier, caseId, request(resubmittedVersion, currentEvidence), newKey());
        status(approved, 200);
        var approvedNode = node(approved);
        UUID secondVerificationId = UUID.fromString(approvedNode.path("verificationId").asText());
        assertThat(approvedNode.path("decision").asText()).isEqualTo("APPROVED");
        assertThat(approvedNode.path("caseStatus").asText()).isEqualTo("VERIFIED");
        assertThat(approvedNode.path("caseVersion").asLong()).isEqualTo(resubmittedVersion + 1);
        assertThat(approvedNode.path("blockchainStatus").asText()).isEqualTo("PENDING_ON_CHAIN");

        assertThat(count("verifications", "aid_case_id", caseId)).isEqualTo(2);
        assertThat(jdbc.queryForMap("""
                SELECT decision, reviewed_case_version, notes FROM verifications WHERE id = ?
                """, firstVerificationId))
                .containsEntry("decision", "CHANGES_REQUESTED")
                .containsEntry("reviewed_case_version", initialVersion)
                .containsEntry("notes", reason);
        assertThat(jdbc.queryForMap("""
                SELECT decision, reviewed_case_version FROM verifications WHERE id = ?
                """, secondVerificationId))
                .containsEntry("decision", "APPROVED")
                .containsEntry("reviewed_case_version", resubmittedVersion);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_CHANGES_REQUESTED'
                """, Long.class, caseId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_VERIFIED'
                """, Long.class, caseId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE a.aid_case_id = ?
                """, Long.class, caseId)).isEqualTo(1L);
    }

    @Test
    void competingApproveAndRejectDecisionsCommitOnlyOneOutcome() throws Exception {
        var submitted = create();
        UUID caseId = caseId(submitted);
        long version = submitted.path("version").asLong();
        var ids = evidenceIds(submitted);
        var secondVerifier = actor(ORG_A, "VERIFIER", false);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var approveFuture = executor.submit(() -> {
                start.await();
                return approve(verifier, caseId, request(version, ids), newKey());
            });
            var rejectFuture = executor.submit(() -> {
                start.await();
                return reject(secondVerifier, caseId,
                        requestWithReason(version, ids, "Conflicting duplicate household claim"), newKey());
            });
            start.countDown();

            var responses = List.of(approveFuture.get(35, TimeUnit.SECONDS), rejectFuture.get(35, TimeUnit.SECONDS));
            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            String winningDecision = null;
            for (var response : responses) {
                if (response.statusCode() == 200) {
                    status(response, 200);
                    winningDecision = node(response).path("decision").asText();
                } else {
                    status(response, 409);
                    assertThat(node(response).path("code").asText()).isIn("VERSION_CONFLICT", "INVALID_STATE");
                }
            }
            assertThat(winningDecision).isIn("APPROVED", "REJECTED");
            assertThat(count("verifications", "aid_case_id", caseId)).isEqualTo(1);
            if ("APPROVED".equals(winningDecision)) {
                assertApprovalCounts(caseId, 1);
            } else {
                assertThat(jdbc.queryForMap("SELECT status FROM aid_cases WHERE id = ?", caseId))
                        .containsEntry("status", "REJECTED");
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                        WHERE a.aid_case_id = ?
                        """, Long.class, caseId)).isZero();
            }
        }
    }

    private HttpResponse<String> approve(Actor actor, UUID caseId, Map<String, Object> request, String key)
            throws Exception {
        return actor.browser().send("POST", "/api/cases/" + caseId + "/verify", request, key, true);
    }

    private HttpResponse<String> reject(Actor actor, UUID caseId, Map<String, Object> request, String key)
            throws Exception {
        return actor.browser().send("POST", "/api/cases/" + caseId + "/reject", request, key, true);
    }

    private HttpResponse<String> requestChanges(Actor actor, UUID caseId, Map<String, Object> request, String key)
            throws Exception {
        return actor.browser().send("POST", "/api/cases/" + caseId + "/request-changes", request, key, true);
    }

    private static UUID caseId(JsonNode submitted) {
        return UUID.fromString(submitted.path("id").asText());
    }

    private static List<String> evidenceIds(JsonNode submitted) {
        var versions = submitted.path("evidenceVersions");
        var ids = new ArrayList<String>();
        for (int index = 0; index < versions.size(); index++) {
            ids.add(versions.get(index).path("id").asText());
        }
        return ids;
    }

    private static Map<String, Object> request(long version, List<String> evidenceIds) {
        return Map.of("expectedVersion", version, "reviewedEvidenceVersionIds", evidenceIds);
    }

    private static Map<String, Object> requestWithReason(long version, List<String> evidenceIds, String reason) {
        return Map.of("expectedVersion", version, "reviewedEvidenceVersionIds", evidenceIds, "reason", reason);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private void assertStillSubmitted(JsonNode submitted) {
        UUID caseId = caseId(submitted);
        assertThat(jdbc.queryForMap("SELECT status, version FROM aid_cases WHERE id = ?", caseId))
                .containsEntry("status", "SUBMITTED")
                .containsEntry("version", submitted.path("version").asLong());
        assertApprovalCounts(caseId, 0);
    }

    private void assertApprovalCounts(UUID caseId, long expected) {
        assertThat(count("verifications", "aid_case_id", caseId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_events WHERE aid_case_id = ? AND event_type = 'CASE_VERIFIED'
                """, Long.class, caseId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id = j.audit_event_id
                WHERE a.aid_case_id = ?
                """, Long.class, caseId)).isEqualTo(expected);
    }
}
