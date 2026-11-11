package com.aidbridge.audit.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.casefile.entity.CaseRevision;
import com.aidbridge.casefile.repository.CaseRevisionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class LocalAuditService {
    private final AuditEventRepository events;
    private final CaseRevisionRepository revisions;
    private final ObjectMapper json;

    public void append(UUID orgId, UUID caseId, UUID actorId, UUID entityId, String eventType, Object privatePayload) {
        String entityType = caseId.equals(entityId) ? "AID_CASE" : "EVIDENCE_VERSION";
        events.save(new AuditEvent(orgId, caseId, actorId, entityType, entityId,
                eventType, UUID.randomUUID(), json.writeValueAsString(privatePayload)));
    }

    public UUID appendVerification(
            UUID orgId, UUID caseId, UUID actorId, UUID verificationId,
            long reviewedCaseVersion, String eventType
    ) {
        String payload = json.writeValueAsString(Map.of(
                "payloadVersion", 1,
                "verificationId", verificationId,
                "reviewedCaseVersion", reviewedCaseVersion));
        return events.save(new AuditEvent(orgId, caseId, actorId, "VERIFICATION", verificationId,
                eventType, verificationId, payload)).getId();
    }

    public UUID appendCampaign(
            UUID orgId, UUID caseId, UUID actorId, UUID campaignId,
            String eventType, Object privatePayload
    ) {
        return events.save(new AuditEvent(
                orgId,
                caseId,
                actorId,
                "CAMPAIGN",
                campaignId,
                eventType,
                UUID.randomUUID(),
                json.writeValueAsString(privatePayload)
        )).getId();
    }

    public void revision(UUID orgId, UUID caseId, long version, Object snapshot) {
        revisions.save(new CaseRevision(orgId, caseId, version, json.writeValueAsString(snapshot)));
    }

    public boolean changedAfterReview(UUID caseId, UUID orgId) {
        return !revisions.findChangedAfterReview(caseId, orgId, Limit.of(1)).isEmpty();
    }
}
