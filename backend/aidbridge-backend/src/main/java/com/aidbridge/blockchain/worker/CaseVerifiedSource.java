package com.aidbridge.blockchain.worker;

import java.util.UUID;

/** Private source rows for one CASE_VERIFIED job (native projection; never serialized to clients). */
public interface CaseVerifiedSource {
    UUID getJobId();

    UUID getVerificationId();

    /** audit_events.occurred_at formatted in UTC as YYYY-MM-DDTHH:mm:ss.SSSZ by PostgreSQL. */
    String getOccurredAtUtc();

    String getPayloadPrivate();

    UUID getOrganizationId();

    String getOrganizationStatus();

    byte[] getOrgPublicRef();

    String getRelayerAddress();

    UUID getCaseId();

    byte[] getCasePublicRef();

    /** case_revisions.snapshot_private at the reviewed case version; null if the revision is missing. */
    String getReviewedSnapshot();
}
