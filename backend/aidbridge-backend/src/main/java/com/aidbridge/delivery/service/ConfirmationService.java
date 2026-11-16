package com.aidbridge.delivery.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.delivery.dto.request.ConfirmationRequest;
import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.entity.DeliveryConfirmation;
import com.aidbridge.delivery.entity.Dispute;
import com.aidbridge.delivery.repository.*;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ConfirmationService {

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final CampaignItemRepository campaignItems;
    private final DeliveryRepository deliveries;
    private final IdempotencyService idempotency;
    private final DeliveryConfirmationRepository confirmations;
    private final DeliveryEvidenceLinkRepository evidenceLinks;
    private final DisputeRepository disputes;
    private final AuditEventRepository auditEvents;
    private final BlockchainJobRepository jobs;
    private final ObjectMapper json;
    private final DeliveryVoidRepository deliveryVoids;
    private final GoodsTotalsQueryService goodsTotals;

    @Transactional
    public Result<ConfirmationResponse> confirm(
            org.springframework.security.core.Authentication authentication,
            UUID deliveryId,
            String key,
            ConfirmationRequest request
    ) {
        var memberships = organizations.currentMemberships(authentication);

        List<UUID> organizationIds = memberships.stream()
                .map(membership -> membership.getOrganization().getId())
                .distinct()
                .toList();

        if (organizationIds.isEmpty()) {
            throw BusinessException.missing();
        }

        var reference = deliveries.findScopedRef(
                deliveryId,
                organizationIds
        ).orElseThrow(BusinessException::missing);

        UUID organizationId = reference.getOrganizationId();

        var aidCase = cases.lockScoped(
                reference.getCaseId(),
                List.of(organizationId)
        ).orElseThrow(BusinessException::missing);

        var campaign = campaigns.lockByCaseAndOrganization(
                aidCase.getId(),
                organizationId
        ).orElseThrow(BusinessException::missing);

        if (!campaign.getId().equals(reference.getCampaignId())) {
            throw BusinessException.missing();
        }

        var itemIds = campaignItems
                .findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                        campaign.getId(),
                        organizationId
                )
                .stream()
                .map(item -> item.getId())
                .toList();

        if (!itemIds.isEmpty()) {
            campaignItems.lockByIds(campaign.getId(), itemIds);
        }

        var delivery = deliveries.lockScoped(
                deliveryId,
                aidCase.getId(),
                campaign.getId(),
                organizationId
        ).orElseThrow(BusinessException::missing);

        var membership = organizations.currentMemberships(authentication)
                .stream()
                .filter(candidate ->
                        organizationId.equals(
                                candidate.getOrganization().getId()
                        )
                )
                .findFirst()
                .orElseThrow(BusinessException::missing);

        if (!membership.getRoles().contains(
                OrganizationMembership.Role.DELIVERY_CONFIRMER
        )) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Access is denied"
            );
        }

        UUID reviewerId = membership.getUser().getId();

        if (reviewerId.equals(delivery.getRecorderId())) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "SELF_CONFIRMATION",
                    "A recorder cannot confirm their own delivery."
            );
        }

        return idempotency.execute(
                reviewerId,
                organizationId,
                "POST:/api/deliveries/" + deliveryId + "/confirm",
                key,
                request,
                ConfirmationResponse.class,
                () -> confirmNew(
                        aidCase,
                        campaign,
                        delivery,
                        organizationId,
                        reviewerId,
                        request
                )
        );
    }

    private List<UUID> validateNewDecision(
            Delivery delivery,
            UUID organizationId,
            ConfirmationRequest request
    ) {
        if (delivery.getOutcome() != Delivery.Outcome.DELIVERED) {
            throw BusinessException.state();
        }

        if (deliveryVoids.countScoped(organizationId, delivery.getId()) > 0) {
            throw BusinessException.state();
        }

        long confirmedCount = confirmations.countConfirmedInOrganization(
                organizationId,
                delivery.getId()
        );

        if (confirmedCount > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ALREADY_CONFIRMED",
                    "The delivery has already been confirmed."
            );
        }

        long unresolvedDisputeCount =
                disputes.countByCaseAndStatusesScoped(
                        organizationId,
                        delivery.getCaseId(),
                        List.of(
                                Dispute.Status.OPEN,
                                Dispute.Status.UNDER_REVIEW
                        )
                );

        if (unresolvedDisputeCount > 0) {
            throw BusinessException.disputeOpen();
        }

        List<UUID> snapshotIds =
                evidenceLinks.findSnapshotVersionIdsScoped(
                        delivery.getId(),
                        delivery.getCaseId(),
                        organizationId
                );

        List<UUID> reviewedIds = request.reviewedEvidenceVersionIds();

        if (reviewedIds == null
                || reviewedIds.isEmpty()
                || reviewedIds.stream().anyMatch(id -> id == null)) {
            throw BusinessException.invalid();
        }

        var reviewedSet = new HashSet<>(reviewedIds);
        var snapshotSet = new HashSet<>(snapshotIds);

        if (reviewedSet.size() != reviewedIds.size()
                || !reviewedSet.equals(snapshotSet)) {
            throw BusinessException.invalid();
        }

        return List.copyOf(snapshotIds);
    }

    private Result<ConfirmationResponse> confirmNew(
            AidCase aidCase,
            Campaign campaign,
            Delivery delivery,
            UUID organizationId,
            UUID reviewerId,
            ConfirmationRequest request
    ) {
        List<UUID> reviewedEvidenceIds =
                validateNewDecision(delivery, organizationId, request);

        if (aidCase.getStatus() != AidCase.Status.IN_PROGRESS
                || (campaign.getStatus() != Campaign.Status.PUBLISHED
                && campaign.getStatus() != Campaign.Status.FULFILLED)) {
            throw BusinessException.state();
        }

        Instant reviewedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        DeliveryConfirmation confirmation =
                confirmations.saveAndFlush(new DeliveryConfirmation(
                        delivery.getId(),
                        reviewerId,
                        request.decision(),
                        request.reason(),
                        reviewedAt
                ));

        UUID disputeId = null;
        UUID auditEventId;
        String blockchainStatus;

        if ("CONFIRMED".equals(confirmation.getDecision())) {
            AuditEvent event = saveConfirmationAudit(
                    organizationId,
                    aidCase.getId(),
                    reviewerId,
                    delivery,
                    confirmation,
                    reviewedEvidenceIds,
                    null,
                    "DELIVERY_CONFIRMED"
            );

            long nextSequence = jobs.maxChainSequence(aidCase.getId()) + 1;

            jobs.saveAndFlush(new BlockchainJob(event, nextSequence));

            auditEventId = event.getId();
            blockchainStatus = "PENDING_ON_CHAIN";
        } else {
            Dispute dispute = disputes.saveAndFlush(
                    Dispute.openForDelivery(
                            aidCase.getId(),
                            delivery.getId(),
                            reviewerId,
                            confirmation.getReason(),
                            reviewedAt
                    )
            );

            disputeId = dispute.getId();

            saveConfirmationAudit(
                    organizationId,
                    aidCase.getId(),
                    reviewerId,
                    delivery,
                    confirmation,
                    reviewedEvidenceIds,
                    disputeId,
                    "DELIVERY_REVIEW_REQUIRED"
            );

            Map<String, Object> disputePayload = new LinkedHashMap<>();
            disputePayload.put("payloadVersion", 1);
            disputePayload.put("disputeId", disputeId);
            disputePayload.put("deliveryId", delivery.getId());
            disputePayload.put("confirmationId", confirmation.getId());
            disputePayload.put("status", dispute.getStatus().name());

            AuditEvent disputeEvent = auditEvents.save(new AuditEvent(
                    organizationId,
                    aidCase.getId(),
                    reviewerId,
                    "DISPUTE",
                    disputeId,
                    "DISPUTE_OPENED",
                    disputeId,
                    json.writeValueAsString(disputePayload)
            ));

            auditEventId = disputeEvent.getId();
            blockchainStatus = "NOT_APPLICABLE";
        }

        List<ConfirmationResponse.GoodsTotal> totals =
                goodsTotals.readForAuthorizedCampaign(
                        organizationId,
                        campaign.getId()
                );
        if ("CONFIRMED".equals(confirmation.getDecision())
                && !totals.isEmpty()
                && totals.stream().allMatch(
                total -> total.confirmedQty() >= total.targetQty()
        )) {
            campaign.markFulfilled();
        }

        ConfirmationResponse response = new ConfirmationResponse(
                confirmation.getId(),
                delivery.getId(),
                confirmation.getDecision(),
                reviewerId,
                reviewedAt,
                disputeId,
                totals,
                auditEventId,
                blockchainStatus
        );

        return new Result<>(response, 200, null, false);
    }

    private AuditEvent saveConfirmationAudit(
            UUID organizationId,
            UUID caseId,
            UUID reviewerId,
            Delivery delivery,
            DeliveryConfirmation confirmation,
            List<UUID> reviewedEvidenceIds,
            UUID disputeId,
            String eventType
    ) {
        byte[] deliveryRef = delivery.getDeliveryRef();

        if (deliveryRef == null || deliveryRef.length != 32) {
            throw BusinessException.state();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("confirmationId", confirmation.getId());
        payload.put("deliveryId", delivery.getId());
        payload.put("campaignId", delivery.getCampaignId());
        payload.put(
                "deliveryRef",
                "0x" + HexFormat.of().formatHex(deliveryRef)
        );
        payload.put("decision", confirmation.getDecision());
        payload.put("reviewedAt", confirmation.getReviewedAt());
        payload.put("reviewedEvidenceVersionIds", reviewedEvidenceIds);

        if (disputeId != null) {
            payload.put("disputeId", disputeId);
        }

        return auditEvents.save(new AuditEvent(
                organizationId,
                caseId,
                reviewerId,
                "DELIVERY",
                delivery.getId(),
                eventType,
                confirmation.getId(),
                json.writeValueAsString(payload)
        ));
    }

    private List<ConfirmationResponse.GoodsTotal> readGoodsTotals(
            UUID organizationId,
            UUID campaignId
    ) {
        var items = campaignItems
                .findByCampaign_IdAndOrganizationIdOrderByCreatedAtAscIdAsc(
                        campaignId,
                        organizationId
                );

        if (items.isEmpty()) {
            throw BusinessException.state();
        }

        List<UUID> itemIds = items.stream()
                .map(item -> item.getId())
                .toList();

        var progressByItem = campaignItems.progressTotals(itemIds)
                .stream()
                .collect(Collectors.toMap(
                        CampaignItemRepository.ItemProgress::getItemId,
                        progress -> progress
                ));

        var deliveredByItem = deliveries.deliveredTotals(campaignId, itemIds)
                .stream()
                .collect(Collectors.toMap(
                        DeliveryRepository.DeliveredTotal::getItemId,
                        DeliveryRepository.DeliveredTotal::getQuantity
                ));

        var confirmedByItem = confirmations.confirmedTotalsScoped(
                        organizationId,
                        campaignId,
                        Delivery.Outcome.DELIVERED
                )
                .stream()
                .collect(Collectors.toMap(
                        DeliveryConfirmationRepository.ConfirmedTotal::getItemId,
                        DeliveryConfirmationRepository.ConfirmedTotal::getQuantity
                ));

        return items.stream()
                .map(item -> {
                    var progress = progressByItem.get(item.getId());

                    long pledged = progress == null
                            ? 0L : progress.getPledgedQty();

                    long received = progress == null
                            ? 0L : progress.getReceivedQty();

                    long delivered =
                            deliveredByItem.getOrDefault(item.getId(), 0L);

                    long confirmed =
                            confirmedByItem.getOrDefault(item.getId(), 0L);

                    long available = received - delivered;

                    if (available < 0 || confirmed > delivered) {
                        throw BusinessException.state();
                    }

                    return new ConfirmationResponse.GoodsTotal(
                            item.getId(),
                            item.getItemName(),
                            item.getUnit(),
                            item.getTargetQuantity(),
                            pledged,
                            received,
                            delivered,
                            confirmed,
                            available
                    );
                })
                .toList();
    }
}