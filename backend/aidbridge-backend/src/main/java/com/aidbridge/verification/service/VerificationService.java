package com.aidbridge.verification.service;

import com.aidbridge.audit.service.LocalAuditService;
import com.aidbridge.casefile.dto.response.EvidenceVersionResponse;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.casefile.repository.CaseRevisionRepository;
import com.aidbridge.casefile.repository.EvidenceRepository;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.verification.dto.request.DecisionRequest;
import com.aidbridge.verification.dto.response.DecisionResponse;
import com.aidbridge.verification.entity.Verification;
import com.aidbridge.verification.repository.VerificationRepository;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CaseRevisionRepository revisions;
    private final EvidenceRepository evidence;
    private final VerificationRepository verifications;
    private final VerificationOutboxService outbox;
    private final LocalAuditService audit;
    private final IdempotencyService idempotency;

    @Transactional
    public Result<DecisionResponse> approve(
            Authentication authentication, UUID caseId, String key, DecisionRequest request
    ) {
        return decide(authentication, caseId, "verify", key, request, true,
                (aidCase, organizationId, reviewerId, reviewedVersion, reason) -> {
                    var verification = verifications.saveAndFlush(
                            Verification.approved(aidCase, reviewerId, reviewedVersion, reason)
                    );
                    aidCase.markVerified();
                    cases.flush();
                    UUID auditEventId = outbox.enqueueApprovedVerification(organizationId, verification.getId());
                    return response(verification, aidCase, reviewerId, auditEventId, "PENDING_ON_CHAIN");
                });
    }

    @Transactional
    public Result<DecisionResponse> reject(
            Authentication authentication, UUID caseId, String key, DecisionRequest request
    ) {
        return decide(authentication, caseId, "reject", key, request, false,
                (aidCase, organizationId, reviewerId, reviewedVersion, reason) -> {
                    var verification = verifications.saveAndFlush(
                            Verification.rejected(aidCase, reviewerId, reviewedVersion, reason)
                    );
                    aidCase.markRejected();
                    cases.flush();
                    UUID auditEventId = audit.appendVerification(
                            organizationId, aidCase.getId(), reviewerId,
                            verification.getId(), reviewedVersion, "CASE_REJECTED"
                    );
                    return response(verification, aidCase, reviewerId, auditEventId, "NOT_APPLICABLE");
                });
    }

    @Transactional
    public Result<DecisionResponse> requestChanges(
            Authentication authentication, UUID caseId, String key, DecisionRequest request
    ) {
        return decide(authentication, caseId, "request-changes", key, request, false,
                (aidCase, organizationId, reviewerId, reviewedVersion, reason) -> {
                    var verification = verifications.saveAndFlush(
                            Verification.changesRequested(aidCase, reviewerId, reviewedVersion, reason)
                    );
                    aidCase.markChangesRequested();
                    cases.flush();
                    UUID auditEventId = audit.appendVerification(
                            organizationId, aidCase.getId(), reviewerId,
                            verification.getId(), reviewedVersion, "CASE_CHANGES_REQUESTED"
                    );
                    return response(verification, aidCase, reviewerId, auditEventId, "NOT_APPLICABLE");
                });
    }

    private Result<DecisionResponse> decide(
            Authentication authentication,
            UUID caseId,
            String actionPath,
            String key,
            DecisionRequest request,
            boolean reasonOptional,
            DecisionTransition transition
    ) {
        var memberships = organizations.currentMemberships(authentication);
        var organizationIds = memberships.stream()
                .map(member -> member.getOrganization().getId())
                .toList();
        if (organizationIds.isEmpty()) {
            throw BusinessException.missing();
        }
        AidCase aidCase = cases.lockScoped(caseId, organizationIds).orElseThrow(BusinessException::missing);
        UUID organizationId = aidCase.getOrganization().getId();
        var membership = memberships.stream()
                .filter(member -> member.getOrganization().getId().equals(organizationId))
                .findFirst()
                .orElseThrow(BusinessException::missing);
        UUID reviewerId = membership.getUser().getId();
        if (!membership.getRoles().contains(OrganizationMembership.Role.VERIFIER)
                || reviewerId.equals(aidCase.getCreatedBy().getId())) {
            throw new AccessDeniedException("Access is denied");
        }
        return idempotency.execute(
                reviewerId, organizationId, "POST:/api/cases/" + caseId + "/" + actionPath,
                key, request, DecisionResponse.class,
                () -> applyDecision(aidCase, organizationId, reviewerId, request, reasonOptional, transition)
        );
    }

    private Result<DecisionResponse> applyDecision(
            AidCase aidCase,
            UUID organizationId,
            UUID reviewerId,
            DecisionRequest request,
            boolean reasonOptional,
            DecisionTransition transition
    ) {
        String reason = sanitizeReason(request.reason(), reasonOptional);
        long reviewedVersion = aidCase.getVersion();
        if (reviewedVersion != request.expectedVersion()) {
            throw BusinessException.stale();
        }
        if (aidCase.getStatus() != AidCase.Status.SUBMITTED) {
            throw BusinessException.state();
        }
        var currentEvidenceIds = evidence.current(aidCase.getId(), organizationId).stream()
                .map(EvidenceVersionResponse::id)
                .collect(Collectors.toSet());
        var requestedIds = request.reviewedEvidenceVersionIds();
        var reviewedEvidenceIds = new HashSet<>(requestedIds);
        if (currentEvidenceIds.isEmpty() || reviewedEvidenceIds.size() != requestedIds.size()
                || !reviewedEvidenceIds.equals(currentEvidenceIds)) {
            throw BusinessException.invalid();
        }
        if (!revisions.existsByCaseIdAndOrganizationIdAndCaseVersion(aidCase.getId(), organizationId, reviewedVersion)) {
            throw BusinessException.state();
        }
        DecisionResponse response = transition.apply(aidCase, organizationId, reviewerId, reviewedVersion, reason);
        return new Result<>(response, 200, null, false);
    }

    private static DecisionResponse response(
            Verification verification,
            AidCase aidCase,
            UUID reviewerId,
            UUID auditEventId,
            String blockchainStatus
    ) {
        return new DecisionResponse(
                verification.getId(), aidCase.getId(), verification.getDecision(),
                aidCase.getStatus(), aidCase.getVersion(), reviewerId,
                verification.getVerifiedAt(), auditEventId, blockchainStatus
        );
    }

    private static String sanitizeReason(String input, boolean optional) {
        String value = input == null ? null : input.strip();
        if (value == null || value.isEmpty()) {
            if (optional) {
                return null;
            }
            throw BusinessException.invalid();
        }
        if (value.contains("<") || value.contains(">") || value.contains("://") || value.indexOf('\0') >= 0) {
            throw BusinessException.invalid();
        }
        return value;
    }

    @FunctionalInterface
    private interface DecisionTransition {
        DecisionResponse apply(
                AidCase aidCase, UUID organizationId, UUID reviewerId, long reviewedVersion, String reason
        );
    }
}
