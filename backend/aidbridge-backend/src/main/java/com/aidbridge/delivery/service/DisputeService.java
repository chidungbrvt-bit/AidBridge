package com.aidbridge.delivery.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.casefile.service.CaseService;
import com.aidbridge.contribution.repository.ContributionRepository;
import com.aidbridge.delivery.dto.request.DisputeRequest;
import com.aidbridge.delivery.dto.request.DonorDisputeRequest;
import com.aidbridge.delivery.dto.request.ResolveDisputeRequest;
import com.aidbridge.delivery.dto.response.DisputeResponse;
import com.aidbridge.delivery.dto.response.DisputeReviewResponse;
import com.aidbridge.delivery.dto.response.DonorDisputeResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.entity.DeliveryVoid;
import com.aidbridge.delivery.entity.Dispute;
import com.aidbridge.delivery.repository.DeliveryEvidenceLinkRepository;
import com.aidbridge.delivery.repository.DeliveryItemRepository;
import com.aidbridge.delivery.repository.DeliveryRepository;
import com.aidbridge.delivery.repository.DeliveryVoidRepository;
import com.aidbridge.delivery.repository.DisputeRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership.Role;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.user.entity.User.PlatformRole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final ContributionRepository contributions;
    private final DeliveryRepository deliveries;
    private final DeliveryVoidRepository deliveryVoids;
    private final DisputeRepository disputes;
    private final AuditEventRepository auditEvents;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;
    private final CampaignItemRepository campaignItems;
    private final DeliveryEvidenceLinkRepository evidenceLinks;
    private final GoodsTotalsQueryService goodsTotals;
    private final DeliveryItemRepository deliveryItems;
    private final CaseService caseService;

    @Transactional
    public Result<DisputeResponse> open(
            Authentication authentication,
            UUID caseId,
            String key,
            DisputeRequest request
    ) {
        List<UUID> organizationIds = organizations.currentMemberships(authentication)
                .stream()
                .filter(membership -> !membership.getRoles().isEmpty())
                .map(membership -> membership.getOrganization().getId())
                .distinct()
                .toList();

        if (organizationIds.isEmpty()) {
            throw BusinessException.missing();
        }

        AidCase aidCase = cases.lockScoped(caseId, organizationIds)
                .orElseThrow(BusinessException::missing);
        UUID organizationId = aidCase.getOrganization().getId();

        var membership = organizations.currentMemberships(authentication)
                .stream()
                .filter(candidate -> organizationId.equals(candidate.getOrganization().getId()))
                .filter(candidate -> !candidate.getRoles().isEmpty())
                .findFirst()
                .orElseThrow(BusinessException::missing);

        var roles = membership.getRoles();
        if (!roles.contains(Role.ORG_ADMIN)
                && !roles.contains(Role.VERIFIER)
                && !roles.contains(Role.DELIVERY_CONFIRMER)) {
            throw new AccessDeniedException("Access is denied");
        }

        UUID actorId = membership.getUser().getId();
        return idempotency.execute(
                actorId,
                organizationId,
                "POST:/api/cases/" + caseId + "/disputes",
                key,
                request,
                DisputeResponse.class,
                () -> openNew(aidCase, organizationId, actorId, request)
        );
    }

    @Transactional
    public Result<DonorDisputeResponse> openForDonor(
            Authentication authentication,
            UUID campaignId,
            String key,
            DonorDisputeRequest request
    ) {
        var donor = organizations.currentUser(authentication);
        if (!donor.isDonorEnabled()) {
            throw new AccessDeniedException("Access is denied");
        }

        var target = campaigns.findPublicRef(
                campaignId, Campaign.Status.DRAFT, Organization.Status.APPROVED
        ).orElseThrow(BusinessException::missing);
        UUID organizationId = target.getOrganizationId();
        UUID donorId = donor.getId();

        if (!contributions.existsByCampaign_IdAndOrganizationIdAndDonorUserId(
                campaignId, organizationId, donorId
        )) {
            throw BusinessException.missing();
        }

        AidCase aidCase = cases.lockScoped(target.getCaseId(), List.of(organizationId))
                .orElseThrow(BusinessException::missing);

        return idempotency.execute(
                donorId,
                organizationId,
                "POST:/api/campaigns/" + campaignId + "/disputes",
                key,
                request,
                DonorDisputeResponse.class,
                () -> {
                    Dispute dispute = openDispute(aidCase, organizationId, donorId, null, request.reason());
                    var response = new DonorDisputeResponse(
                            dispute.getId(), dispute.getStatus().name(), dispute.getCreatedAt()
                    );
                    return new Result<>(response, 201, "/api/disputes/" + dispute.getId(), false);
                }
        );
    }

    private Result<DisputeResponse> openNew(
            AidCase aidCase,
            UUID organizationId,
            UUID actorId,
            DisputeRequest request
    ) {
        Dispute dispute = openDispute(
                aidCase, organizationId, actorId, request.deliveryId(), request.reason()
        );

        var response = new DisputeResponse(
                dispute.getId(), dispute.getCaseId(), dispute.getDeliveryId(),
                dispute.getStatus().name(), dispute.getReason(), null,
                dispute.getCreatedAt(), dispute.getUpdatedAt(), null
        );
        return new Result<>(response, 201, "/api/disputes/" + dispute.getId(), false);
    }

    private Dispute openDispute(
            AidCase aidCase,
            UUID organizationId,
            UUID actorId,
            UUID deliveryId,
            String reason
    ) {
        UUID caseId = aidCase.getId();
        AidCase.Status caseStatus = aidCase.getStatus();
        Instant previousClosedAt = null;
        boolean reopened = caseStatus == AidCase.Status.COMPLETED;

        if (caseStatus == AidCase.Status.VERIFIED) {
            if (deliveryId != null) {
                throw BusinessException.missing();
            }
        } else {
            if (caseStatus != AidCase.Status.IN_PROGRESS && !reopened) {
                throw BusinessException.state();
            }

            Campaign campaign = campaigns.lockByCaseAndOrganization(caseId, organizationId)
                    .orElseThrow(BusinessException::state);

            Campaign.Status campaignStatus = campaign.getStatus();
            if (reopened ? campaignStatus != Campaign.Status.CLOSED
                    : campaignStatus != Campaign.Status.PUBLISHED
                      && campaignStatus != Campaign.Status.FULFILLED) {
                throw BusinessException.state();
            }

            if (deliveryId != null) {
                requireDisputableDelivery(deliveryId, caseId, campaign.getId(), organizationId);
            }

            if (reopened) {
                previousClosedAt = campaign.getClosedAt();
                campaign.reopenForDispute();
                aidCase.markInProgress();
            }
        }

        Instant openedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Dispute dispute = deliveryId == null
                ? Dispute.openForCase(caseId, actorId, reason, openedAt)
                : Dispute.openForDelivery(caseId, deliveryId, actorId, reason, openedAt);
        disputes.save(dispute);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("disputeId", dispute.getId());
        payload.put("status", dispute.getStatus().name());
        payload.put("caseReopened", reopened);
        if (deliveryId != null) {
            payload.put("deliveryId", deliveryId);
        }
        if (previousClosedAt != null) {
            payload.put("previousClosedAt", previousClosedAt);
        }

        auditEvents.save(new AuditEvent(
                organizationId, caseId, actorId,
                "DISPUTE", dispute.getId(), "DISPUTE_OPENED",
                dispute.getId(), json.writeValueAsString(payload)
        ));

        return dispute;
    }

    private void requireDisputableDelivery(
            UUID deliveryId,
            UUID caseId,
            UUID campaignId,
            UUID organizationId
    ) {
        Delivery delivery = deliveries.lockScoped(deliveryId, caseId, campaignId, organizationId)
                .orElseThrow(BusinessException::missing);

        if (delivery.getOutcome() != Delivery.Outcome.DELIVERED
                || deliveryVoids.countScoped(organizationId, deliveryId) > 0) {
            throw BusinessException.missing();
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DisputeReviewResponse review(Authentication authentication, UUID disputeId) {
        requirePlatformAdmin(authentication);
        var scope = openReviewScope(disputeId);
        var dispute = scope.dispute();
        DisputeReviewResponse.Delivery delivery = null;
        if (dispute.getDeliveryId() != null) {
            var stored = deliveries.findByIdAndCaseId(dispute.getDeliveryId(), dispute.getCaseId())
                    .orElseThrow(BusinessException::missing);
            var items = deliveryItems.findReviewItems(stored.getId(), scope.organizationId()).stream()
                    .map(item -> new DisputeReviewResponse.Item(
                            item.getCampaignItemId(), item.getItemName(), item.getUnit(), item.getQuantity()))
                    .toList();
            delivery = new DisputeReviewResponse.Delivery(stored.getId(), stored.getRecordedAt(),
                    items, reviewEvidence(scope));
        }
        return new DisputeReviewResponse(dispute.getId(), dispute.getCaseId(), dispute.getStatus().name(),
                dispute.getReason(), dispute.getCreatedAt(), delivery);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CaseService.Content reviewEvidenceContent(Authentication authentication, UUID disputeId, UUID versionId) {
        UUID actorId = requirePlatformAdmin(authentication);
        var scope = openReviewScope(disputeId);
        if (scope.dispute().getDeliveryId() == null || !reviewEvidence(scope).contains(versionId)) {
            throw BusinessException.missing();
        }
        return caseService.contentForDispute(scope.organizationId(), scope.dispute().getCaseId(),
                actorId, disputeId, versionId);
    }

    private record ReviewScope(Dispute dispute, UUID organizationId) { }

    private ReviewScope openReviewScope(UUID disputeId) {
        var reference = disputes.findRefForResolution(disputeId).orElseThrow(BusinessException::missing);
        var dispute = disputes.findScoped(reference.getOrganizationId(), disputeId)
                .filter(candidate -> reference.getCaseId().equals(candidate.getCaseId()))
                .filter(candidate -> candidate.getStatus() == Dispute.Status.OPEN)
                .orElseThrow(BusinessException::missing);
        return new ReviewScope(dispute, reference.getOrganizationId());
    }

    private List<UUID> reviewEvidence(ReviewScope scope) {
        return evidenceLinks.findSnapshotVersionIdsScoped(
                scope.dispute().getDeliveryId(), scope.dispute().getCaseId(), scope.organizationId());
    }

    private UUID requirePlatformAdmin(Authentication authentication) {
        var actor = organizations.currentUser(authentication);
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new AccessDeniedException("Access is denied");
        }
        return actor.getId();
    }

    @Transactional
    public Result<DisputeResponse> resolve(
            Authentication authentication,
            UUID disputeId,
            String key,
            ResolveDisputeRequest request
    ) {
        UUID actorId = requirePlatformAdmin(authentication);

        var reference = disputes.findRefForResolution(disputeId)
                .orElseThrow(BusinessException::missing);
        UUID organizationId = reference.getOrganizationId();

        AidCase aidCase = cases.lockScoped(reference.getCaseId(), List.of(organizationId))
                .orElseThrow(BusinessException::missing);

        return idempotency.execute(
                actorId,
                organizationId,
                "POST:/api/disputes/" + disputeId + "/resolve",
                key,
                request,
                DisputeResponse.class,
                () -> resolveNew(aidCase, organizationId, actorId, disputeId, request)
        );
    }

    private Result<DisputeResponse> resolveNew(
            AidCase aidCase,
            UUID organizationId,
            UUID actorId,
            UUID disputeId,
            ResolveDisputeRequest request
    ) {
        UUID caseId = aidCase.getId();
        Dispute dispute = disputes.findScoped(organizationId, disputeId)
                .filter(candidate -> caseId.equals(candidate.getCaseId()))
                .orElseThrow(BusinessException::missing);

        if (dispute.getStatus() != Dispute.Status.OPEN) {
            throw BusinessException.state();
        }

        UUID deliveryId = dispute.getDeliveryId();
        boolean voidDelivery = switch (request.outcome()) {
            case "UPHOLD" -> false;
            case "VOID_DELIVERY" -> true;
            default -> throw BusinessException.invalid();
        };

        if (voidDelivery && deliveryId == null) {
            throw BusinessException.invalid();
        }

        validateResolutionEvidence(
                caseId, organizationId, deliveryId, request.reviewEvidenceVersionIds()
        );

        Instant resolvedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (voidDelivery) {
            applyDeliveryVoid(aidCase, organizationId, actorId, dispute, request.reason(), resolvedAt);
        }

        dispute.resolve(actorId, request.reason(), resolvedAt);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("disputeId", disputeId);
        payload.put("resolution", request.outcome());
        payload.put("reviewEvidenceVersionIds", request.reviewEvidenceVersionIds());
        if (deliveryId != null) {
            payload.put("deliveryId", deliveryId);
        }

        auditEvents.save(new AuditEvent(
                organizationId, caseId, actorId,
                "DISPUTE", disputeId, "DISPUTE_RESOLVED",
                UUID.randomUUID(), json.writeValueAsString(payload)
        ));

        var response = new DisputeResponse(
                disputeId, caseId, deliveryId, dispute.getStatus().name(),
                dispute.getReason(), request.outcome(), dispute.getCreatedAt(),
                dispute.getUpdatedAt(), dispute.getResolvedAt()
        );

        return new Result<>(response, 200, null, false);
    }

    private void validateResolutionEvidence(
            UUID caseId,
            UUID organizationId,
            UUID deliveryId,
            List<UUID> reviewedIds
    ) {
        if (deliveryId == null) {
            if (!reviewedIds.isEmpty()) {
                throw BusinessException.invalid();
            }
            return;
        }

        deliveries.findScopedRef(deliveryId, List.of(organizationId))
                .filter(reference -> caseId.equals(reference.getCaseId()))
                .orElseThrow(BusinessException::missing);

        var snapshot = evidenceLinks.findSnapshotVersionIdsScoped(
                deliveryId, caseId, organizationId
        );
        if (!snapshot.containsAll(reviewedIds)) {
            throw BusinessException.invalid();
        }
    }

    private void applyDeliveryVoid(
            AidCase aidCase,
            UUID organizationId,
            UUID actorId,
            Dispute dispute,
            String reason,
            Instant voidedAt
    ) {
        UUID caseId = aidCase.getId();
        UUID deliveryId = dispute.getDeliveryId();

        Campaign campaign = campaigns.lockByCaseAndOrganization(caseId, organizationId)
                .orElseThrow(BusinessException::state);
        if (aidCase.getStatus() != AidCase.Status.IN_PROGRESS
                || (campaign.getStatus() != Campaign.Status.PUBLISHED
                && campaign.getStatus() != Campaign.Status.FULFILLED)) {
            throw BusinessException.state();
        }

        List<UUID> itemIds = campaignItems
                .findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                        campaign.getId(), organizationId
                )
                .stream()
                .map(CampaignItem::getId)
                .toList();

        if (itemIds.isEmpty()) {
            throw BusinessException.state();
        }
        campaignItems.lockByIds(campaign.getId(), itemIds);

        Delivery delivery = deliveries.lockScoped(
                deliveryId, caseId, campaign.getId(), organizationId
        ).orElseThrow(BusinessException::missing);

        if (delivery.getOutcome() != Delivery.Outcome.DELIVERED
                || deliveryVoids.countScoped(organizationId, deliveryId) > 0) {
            throw BusinessException.state();
        }

        DeliveryVoid voided = deliveryVoids.saveAndFlush(new DeliveryVoid(
                deliveryId, caseId, dispute.getId(), actorId, reason, voidedAt
        ));

        var totals = goodsTotals.readForAuthorizedCampaign(organizationId, campaign.getId());
        if (campaign.getStatus() == Campaign.Status.FULFILLED
                && totals.stream().anyMatch(item -> item.confirmedQty() < item.targetQty())) {
            campaign.markPublishedAfterVoid();
        }

        auditEvents.save(new AuditEvent(
                organizationId, caseId, actorId,
                "DELIVERY", deliveryId, "DELIVERY_VOIDED", voided.getId(),
                json.writeValueAsString(Map.of(
                        "payloadVersion", 1,
                        "deliveryId", deliveryId,
                        "disputeId", dispute.getId(),
                        "deliveryVoidId", voided.getId()
                ))
        ));
    }
}
