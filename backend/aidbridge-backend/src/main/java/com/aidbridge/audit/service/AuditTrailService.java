package com.aidbridge.audit.service;

import com.aidbridge.audit.dto.response.AuditTrailPage;
import com.aidbridge.audit.dto.response.InternalAuditEventResponse;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.audit.repository.AuditEventRepository.TrailRow;
import com.aidbridge.audit.service.AuditChainStatusMapper.ChainInput;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only audit trail (T01). Never writes, never calls the chain. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditTrailService {
    static final int MAX_PAGE_SIZE = 100;

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final AuditEventRepository events;
    private final AuditChainStatusMapper chainStatus;

    /**
     * T01. Access: an active member (any role) of the organization that owns the case, same scope rule as
     * GET /api/cases/{id}. Other organizations and platform admins without membership get 404 (no existence leak).
     * Dispute-scoped PA access is deferred until S01-S03 exist.
     */
    public AuditTrailPage<InternalAuditEventResponse> caseTrail(Authentication auth, UUID caseId, int page, int size) {
        validatePage(page, size);
        var orgIds = organizations.currentMemberships(auth).stream()
                .filter(m -> !m.getRoles().isEmpty())
                .map(m -> m.getOrganization().getId())
                .toList();
        if (orgIds.isEmpty()) throw BusinessException.missing();
        AidCase aidCase = cases.findByIdAndOrganization_IdIn(caseId, orgIds).orElseThrow(BusinessException::missing);
        UUID orgId = aidCase.getOrganization().getId();

        // Two statements per page (rows + count), independent of the number of events: no N+1.
        var items = events.findInternalTrail(caseId, orgId, size, (long) page * size).stream()
                .map(this::internal)
                .toList();
        return new AuditTrailPage<>(items, page, size, events.countInternalTrail(caseId, orgId));
    }

    private InternalAuditEventResponse internal(TrailRow row) {
        var actor = row.getActorId() == null ? null
                : new InternalAuditEventResponse.Actor(row.getActorId(), row.getActorName());
        return new InternalAuditEventResponse(row.getEventId(), row.getEventType(), row.getEntityType(),
                Instant.parse(row.getOccurredAtUtc()), actor, row.getLogicalEventId(),
                chainStatus.resolve(chainInput(row), false));
    }

    static ChainInput chainInput(TrailRow row) {
        return new ChainInput(row.getJobStatus(), row.getJobLastError(), row.getJobChainId(), row.getTxHash(),
                row.getTxStatus(), row.getTxBlockNumber(), row.getTxChainId(), row.getTxContractAddress());
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw BusinessException.invalid();
    }
}
