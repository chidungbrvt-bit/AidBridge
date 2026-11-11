package com.aidbridge.campaign.service;

import com.aidbridge.audit.service.LocalAuditService;
import com.aidbridge.campaign.dto.request.CreateCampaignRequest;
import com.aidbridge.campaign.dto.response.CampaignPage;
import com.aidbridge.campaign.dto.response.CampaignResponse;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.campaign.mapper.CampaignMapper;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.entity.CaseNeedItem;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.casefile.repository.CaseNeedItemRepository;
import com.aidbridge.casefile.repository.ConsentRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.verification.repository.VerificationRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

    private static final Set<Campaign.Status> PUBLIC_STATUSES = EnumSet.of(
            Campaign.Status.PUBLISHED, Campaign.Status.FULFILLED, Campaign.Status.CLOSED
    );

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CaseNeedItemRepository caseNeeds;
    private final ConsentRepository consents;
    private final VerificationRepository verifications;
    private final CampaignRepository campaigns;
    private final CampaignItemRepository campaignItems;
    private final LocalAuditService audit;
    private final IdempotencyService idempotency;
    private final CampaignMapper mapper;

    @Transactional
    public Result<CampaignResponse> create(Authentication auth, String key, CreateCampaignRequest request) {
        var memberships = organizations.currentMemberships(auth);
        // Canonical lock order: lock AidCase first.
        AidCase aidCase = cases.lockScoped(request.caseId(), staffOrgIds(memberships))
                .orElseThrow(BusinessException::missing);
        var member = requireOrgAdmin(memberships, aidCase.getOrganization().getId());
        UUID orgId = member.getOrganization().getId(), actorId = member.getUser().getId();

        return idempotency.execute(actorId, orgId, "POST:/api/campaigns", key, request, CampaignResponse.class, () -> {
            String title = sanitize(request.title(), 160), summary = sanitize(request.publicSummary(), 2000);
            List<CaseNeedItem> needs = validateApprovedCase(aidCase, orgId);
            if (campaigns.existsByAidCase_Id(aidCase.getId())) throw BusinessException.state();

            Campaign campaign = campaigns.saveAndFlush(
                    new Campaign(member.getOrganization(), aidCase, member.getUser(), title, summary));
            List<CampaignItem> items = campaignItems.saveAllAndFlush(
                    needs.stream().map(n -> new CampaignItem(campaign, n)).toList());

            audit.appendCampaign(orgId, aidCase.getId(), actorId, campaign.getId(), "CAMPAIGN_CREATED",
                    Map.of("campaignId", campaign.getId(), "caseVersion", aidCase.getVersion(), "itemCount", items.size()));
            return new Result<>(mapper.toResponse(campaign, items), 201, "/api/campaigns/" + campaign.getId(), false);
        });
    }

    @Transactional
    public Result<CampaignResponse> publish(Authentication auth, UUID id, String key) {
        var memberships = organizations.currentMemberships(auth);
        var orgIds = staffOrgIds(memberships);
        Campaign target = campaigns.findWithDetailsById(id)
                .filter(c -> orgIds.contains(c.getOrganization().getId()))
                .orElseThrow(BusinessException::missing);
        var member = requireOrgAdmin(memberships, target.getOrganization().getId());
        UUID orgId = member.getOrganization().getId(), actorId = member.getUser().getId();

        // Canonical lock order: lock AidCase first, then lock Campaign.
        AidCase aidCase = cases.lockScoped(target.getAidCase().getId(), List.of(orgId)).orElseThrow(BusinessException::missing);
        Campaign campaign = campaigns.lockScoped(id, List.of(orgId)).orElseThrow(BusinessException::missing);

        return idempotency.execute(actorId, orgId, "POST:/api/campaigns/" + id + "/publish",
                key, Map.of(), CampaignResponse.class, () -> {
            if (campaign.getStatus() != Campaign.Status.DRAFT) throw BusinessException.state();
            List<CaseNeedItem> needs = validateApprovedCase(aidCase, orgId);
            List<CampaignItem> items = campaignItems.findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(id, orgId);
            // Item order carries no business meaning and is not stable (equal created_at, random UUID ids),
            // so compare the copied snapshot with the current approved needs as sets.
            Set<ItemSpec> copied = items.stream().map(ItemSpec::fromCampaign).collect(Collectors.toSet());
            Set<ItemSpec> current = needs.stream().map(ItemSpec::fromNeed).collect(Collectors.toSet());
            if (items.isEmpty() || items.size() != needs.size() || !copied.equals(current)) {
                throw BusinessException.stale();
            }
            sanitize(campaign.getTitle(), 160);
            sanitize(campaign.getPublicSummary(), 2000);

            campaign.publish(Instant.now());
            aidCase.markInProgress();
            campaigns.flush();
            cases.flush();

            audit.appendCampaign(orgId, aidCase.getId(), actorId, campaign.getId(), "CAMPAIGN_PUBLISHED",
                    Map.of("campaignId", campaign.getId(), "caseVersion", aidCase.getVersion()));
            return new Result<>(mapper.toResponse(campaign, items), 200, null, false);
        });
    }

    public CampaignPage listPublic(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw BusinessException.invalid();
        var result = campaigns.findPublicPage(
                Organization.Status.APPROVED, PUBLIC_STATUSES, PageRequest.of(page, size, Sort.by("createdAt", "id")));
        var content = result.getContent();
        if (content.isEmpty()) return new CampaignPage(List.of(), page, size, result.getTotalElements());

        Map<UUID, List<CampaignItem>> itemsByCampaign = campaignItems
                .findByCampaign_IdInOrderByCreatedAtAscIdAsc(content.stream().map(Campaign::getId).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getCampaign().getId()));
        Map<UUID, Long> pledged = pledgedTotals(
                itemsByCampaign.values().stream().flatMap(List::stream).toList());
        var dtos = content.stream()
                .map(c -> mapper.toResponse(c, itemsByCampaign.getOrDefault(c.getId(), List.of()), pledged)).toList();
        return new CampaignPage(dtos, page, size, result.getTotalElements());
    }

    public CampaignResponse getById(Authentication auth, UUID id) {
        Campaign campaign = campaigns.findWithDetailsById(id).orElseThrow(BusinessException::missing);
        UUID orgId = campaign.getOrganization().getId();
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) throw BusinessException.missing();
        if (campaign.getStatus() == Campaign.Status.DRAFT && !isOwningOrgAdmin(auth, orgId)) throw BusinessException.missing();
        if (campaign.getStatus() != Campaign.Status.DRAFT && !PUBLIC_STATUSES.contains(campaign.getStatus())) {
            throw BusinessException.missing();
        }
        var items = campaignItems.findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(id, orgId);
        return mapper.toResponse(campaign, items, pledgedTotals(items));
    }

    /** One aggregate query for any number of items (no N+1). Public reads only, so no lock is taken. */
    private Map<UUID, Long> pledgedTotals(List<CampaignItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        return campaignItems.activePledgedTotals(items.stream().map(CampaignItem::getId).toList()).stream()
                .collect(Collectors.toMap(
                        CampaignItemRepository.ItemTotal::getItemId,
                        CampaignItemRepository.ItemTotal::getQuantity));
    }

    private List<CaseNeedItem> validateApprovedCase(AidCase c, UUID orgId) {
        var consent = consents.find(c.getConsentRecordId(), orgId, c.getBeneficiary().getId());
        if (c.getStatus() != AidCase.Status.VERIFIED || campaigns.hasActiveDispute(c.getId())
                || consent == null || consent.basis() == null || consent.capturedAt() == null
                || !consent.publicSummaryAllowed()) {
            throw BusinessException.state();
        }
        sanitize(c.getApproximateArea(), 160);
        var review = verifications.findLatestByCase(c.getId(), orgId, Limit.of(1)).stream()
                .findFirst().orElseThrow(BusinessException::state);
        if (!"APPROVED".equals(review.getDecision())) throw BusinessException.state();
        if (review.getReviewedCaseVersion() == null || review.getReviewedCaseVersion() + 1 != c.getVersion()) {
            throw BusinessException.stale();
        }
        var needs = caseNeeds.findByAidCase_IdAndAidCase_Organization_IdAndRetiredAtVersionIsNullOrderByCreatedAtAscIdAsc(c.getId(), orgId);
        if (needs.isEmpty()) throw BusinessException.state();
        return needs;
    }

    private static List<UUID> staffOrgIds(List<OrganizationMembership> memberships) {
        var ids = memberships.stream().filter(m -> !m.getRoles().isEmpty())
                .map(m -> m.getOrganization().getId()).toList();
        if (ids.isEmpty()) throw BusinessException.missing();
        return ids;
    }

    private static OrganizationMembership requireOrgAdmin(List<OrganizationMembership> memberships, UUID orgId) {
        var member = memberships.stream().filter(m -> m.getOrganization().getId().equals(orgId))
                .findFirst().orElseThrow(BusinessException::missing);
        if (!member.getRoles().contains(OrganizationMembership.Role.ORG_ADMIN)) {
            throw new AccessDeniedException("Access is denied");
        }
        return member;
    }

    private boolean isOwningOrgAdmin(Authentication auth, UUID orgId) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return false;
        try {
            return organizations.currentMemberships(auth).stream()
                    .anyMatch(m -> m.getOrganization().getId().equals(orgId)
                            && m.getRoles().contains(OrganizationMembership.Role.ORG_ADMIN));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String sanitize(String input, int maxLen) {
        String val = input == null ? null : input.strip();
        if (val == null || val.isEmpty() || val.length() > maxLen
                || val.contains("<") || val.contains(">") || val.contains("://") || val.indexOf('\0') >= 0) {
            throw BusinessException.invalid();
        }
        return val;
    }

    private record ItemSpec(UUID needId, String name, String unit, int qty) {
        static ItemSpec fromCampaign(CampaignItem i) {
            return new ItemSpec(i.getCaseNeedItemId(), i.getItemName(), i.getUnit(), i.getTargetQuantity());
        }
        static ItemSpec fromNeed(CaseNeedItem n) {
            return new ItemSpec(n.getId(), n.getItemName(), n.getUnit(), n.getRequestedQuantity());
        }
    }
}
