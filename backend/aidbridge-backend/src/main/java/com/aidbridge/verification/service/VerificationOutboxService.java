package com.aidbridge.verification.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.verification.repository.VerificationRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class VerificationOutboxService {
    private final VerificationRepository verifications;
    private final AuditEventRepository events;
    private final BlockchainJobRepository jobs;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueApprovedVerification(UUID organizationId, UUID verificationId) {
        var verification = verifications.findApproved(organizationId, verificationId)
                .orElseThrow(() -> new EmptyResultDataAccessException(1));
        String payload = json.writeValueAsString(Map.of(
                "payloadVersion", 1,
                "verificationId", verificationId,
                "reviewedCaseVersion", verification.getReviewedCaseVersion()));
        var event = events.save(new AuditEvent(organizationId, verification.getAidCase().getId(),
                verification.getVerifierId(), "VERIFICATION", verificationId, "CASE_VERIFIED",
                verificationId, payload));
        jobs.saveAndFlush(new BlockchainJob(event));
        return event.getId();
    }
}
