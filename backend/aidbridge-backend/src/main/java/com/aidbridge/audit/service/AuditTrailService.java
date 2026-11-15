package com.aidbridge.audit.service;

import com.aidbridge.audit.dto.response.AuditTrailPage;
import com.aidbridge.audit.dto.response.InternalAuditEventResponse;
import com.aidbridge.audit.dto.response.PublicAuditEventResponse;
import com.aidbridge.audit.dto.response.PublicBlockchainStatusResponse;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.audit.repository.AuditEventRepository.TrailRow;
import com.aidbridge.audit.service.AuditChainStatusMapper.ChainInput;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only audit trails (T01 internal, T02 public). Never writes, never calls the chain. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditTrailService {
    static final int MAX_PAGE_SIZE = 100;

    /**
     * T02 allow-list: event type -> role label shown instead of the account. Anything not listed (case
     * create/update/reject/changes, evidence, evidence reads, failed delivery attempts, campaign drafts) is
     * internal only. The label comes from the event type, never from a user lookup.
     */
    static final Map<String, String> PUBLIC_EVENT_ROLES = Map.of(
            "CASE_VERIFIED", "VERIFIER",
            "CAMPAIGN_PUBLISHED", "ORGANIZATION",
            "AID_PLEDGED", "DONOR",
            "COMMITMENT_REDUCED", "DONOR",
            "GOODS_RECEIVED", "ORGANIZATION",
            "DELIVERY_RECORDED", "FIELD_VOLUNTEER");

    /** Same visibility rule as the public campaign read (CampaignService.getById). */
    static final Set<Campaign.Status> PUBLIC_CAMPAIGN_STATUSES =
            EnumSet.of(Campaign.Status.PUBLISHED, Campaign.Status.FULFILLED, Campaign.Status.CLOSED);

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final AuditEventRepository events;
    private final AuditChainStatusMapper chainStatus;
    private final CampaignRepository campaigns;

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

    /**
     * T02. No login. Only campaigns visible on the public campaign page (published, fulfilled, closed, by an
     * approved organization); a draft or unknown campaign is 404, also for its own staff.
     */
    public AuditTrailPage<PublicAuditEventResponse> campaignTrail(String publicCaseRef, int page, int size) {
        validatePage(page, size);
        if (publicCaseRef == null || !publicCaseRef.matches("0x[0-9a-f]{64}")) throw BusinessException.missing();
        Campaign campaign = campaigns.findByPublicCaseRef(java.util.HexFormat.of().parseHex(publicCaseRef.substring(2))).orElseThrow(BusinessException::missing);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED
                || !PUBLIC_CAMPAIGN_STATUSES.contains(campaign.getStatus())) {
            throw BusinessException.missing();
        }
        UUID caseId = campaign.getAidCase().getId();
        UUID orgId = campaign.getOrganization().getId();
        var types = PUBLIC_EVENT_ROLES.keySet();

        var items = events.findPublicTrail(caseId, orgId, types, size, (long) page * size).stream()
                .map(this::publicItem)
                .toList();
        return new AuditTrailPage<>(items, page, size, events.countPublicTrail(caseId, orgId, types));
    }

    private PublicAuditEventResponse publicItem(TrailRow row) {
        return new PublicAuditEventResponse("audit_" + com.aidbridge.shared.util.Hashing.sha256("public-audit:" + row.getEventId()), row.getEventType(),
                Instant.parse(row.getOccurredAtUtc()), PUBLIC_EVENT_ROLES.get(row.getEventType()),
                row.getLogicalEventId(), row.getChainSequence(),
                PublicBlockchainStatusResponse.from(chainStatus.resolve(chainInput(row), true)));
    }

    private InternalAuditEventResponse internal(TrailRow row) {
        var actor = row.getActorId() == null ? null
                : new InternalAuditEventResponse.Actor(row.getActorId(), row.getActorName());
        return new InternalAuditEventResponse(row.getEventId(), row.getEventType(), row.getEntityType(),
                Instant.parse(row.getOccurredAtUtc()), actor, row.getLogicalEventId(), row.getChainSequence(),
                chainStatus.resolve(chainInput(row), false));
    }

    static ChainInput chainInput(TrailRow row) {
        return new ChainInput(row.getJobStatus(), row.getJobLastError(), row.getJobChainId(), row.getTxHash(),
                row.getTxStatus(), row.getTxBlockNumber(), row.getTxChainId(), row.getTxContractAddress(), Boolean.TRUE.equals(row.getReceiptVerified()));
    }

    static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw BusinessException.invalid();
    }
}
