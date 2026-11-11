package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.verification.service.VerificationOutboxService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
class VerificationOutboxIT extends AbstractCaseHttpIT {
    @Autowired VerificationOutboxService outbox;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void storesOnePendingJobAndPrivateSnapshotReferencePerApproval() throws Exception {
        var created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        long version = created.path("version").asLong();
        UUID verificationId = UUID.randomUUID();

        UUID eventId = tx().execute(status -> {
            insertVerification(verificationId, caseId, "APPROVED", version);
            return outbox.enqueueApprovedVerification(ORG_A, verificationId);
        });

        var row = jdbc.queryForMap("""
                SELECT a.event_key, a.event_type, a.entity_type, a.entity_id, a.organization_id,
                    a.aid_case_id, a.actor_user_id, a.payload_private::text AS private_payload,
                    a.public_metadata::text AS public_metadata, a.evidence_commitment,
                    j.status, j.chain_id, octet_length(j.logical_event_id) AS event_id_len,
                    j.chain_sequence, j.schema_version::int AS schema_version
                FROM audit_events a JOIN blockchain_jobs j ON j.audit_event_id = a.id
                WHERE a.id = ?
                """, eventId);
        assertThat(row).containsEntry("event_key", verificationId)
                .containsEntry("event_type", "CASE_VERIFIED")
                .containsEntry("entity_type", "VERIFICATION")
                .containsEntry("entity_id", verificationId)
                .containsEntry("organization_id", ORG_A)
                .containsEntry("aid_case_id", caseId)
                .containsEntry("actor_user_id", verifier.id())
                .containsEntry("public_metadata", "{}")
                .containsEntry("evidence_commitment", null)
                .containsEntry("status", "PENDING")
                .containsEntry("chain_id", 43113L)
                .containsEntry("event_id_len", 32)
                .containsEntry("chain_sequence", 1L)
                .containsEntry("schema_version", 1);
        var payload = json.readTree((String) row.get("private_payload"));
        assertThat(payload.size()).isEqualTo(3);
        assertThat(payload.path("payloadVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("verificationId").asText()).isEqualTo(verificationId.toString());
        assertThat(payload.path("reviewedCaseVersion").asLong()).isEqualTo(version);

        assertThatThrownBy(() -> enqueue(ORG_A, verificationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("audit_events", "entity_id", verificationId)).isEqualTo(1);
        assertThat(count("blockchain_jobs", "audit_event_id", eventId)).isEqualTo(1);
    }

    @Test void rollsBackApprovalAuditAndJobTogether() throws Exception {
        var created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        long version = created.path("version").asLong();
        UUID verificationId = UUID.randomUUID();

        UUID eventId = tx().execute(status -> {
            insertVerification(verificationId, caseId, "APPROVED", version);
            UUID id = outbox.enqueueApprovedVerification(ORG_A, verificationId);
            assertThat(count("blockchain_jobs", "audit_event_id", id)).isEqualTo(1);
            status.setRollbackOnly();
            return id;
        });

        assertThat(count("verifications", "id", verificationId)).isZero();
        assertThat(count("audit_events", "id", eventId)).isZero();
        assertThat(count("blockchain_jobs", "audit_event_id", eventId)).isZero();
    }

    @Test void refusesForeignOrganizationNonApprovalAndLegacyApprovalWithoutSnapshot() throws Exception {
        var created = create();
        UUID caseId = UUID.fromString(created.path("id").asText());
        long version = created.path("version").asLong();
        UUID approved = UUID.randomUUID();
        UUID changesRequested = UUID.randomUUID();
        UUID legacy = UUID.randomUUID();
        insertVerification(approved, caseId, "APPROVED", version);
        insertVerification(changesRequested, caseId, "CHANGES_REQUESTED", version);
        insertVerification(legacy, caseId, "APPROVED", null);

        assertThatThrownBy(() -> enqueue(ORG_B, approved)).isInstanceOf(EmptyResultDataAccessException.class);
        assertThatThrownBy(() -> enqueue(ORG_A, changesRequested)).isInstanceOf(EmptyResultDataAccessException.class);
        assertThatThrownBy(() -> enqueue(ORG_A, legacy)).isInstanceOf(EmptyResultDataAccessException.class);
        assertThat(count("audit_events", "aid_case_id", caseId)).isEqualTo(1); // CASE_CREATED only.
    }

    @Test void requiresAnExistingTransaction() {
        assertThatThrownBy(() -> outbox.enqueueApprovedVerification(ORG_A, UUID.randomUUID()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private UUID enqueue(UUID orgId, UUID verificationId) {
        return tx().execute(status -> outbox.enqueueApprovedVerification(orgId, verificationId));
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private void insertVerification(UUID id, UUID caseId, String decision, Long version) {
        jdbc.update("""
                INSERT INTO verifications (id, aid_case_id, verifier_id, decision, reviewed_case_version)
                VALUES (?, ?, ?, ?, ?)
                """, id, caseId, verifier.id(), decision, version);
    }
}
