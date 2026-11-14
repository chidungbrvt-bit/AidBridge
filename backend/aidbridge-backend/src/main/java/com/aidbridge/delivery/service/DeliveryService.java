package com.aidbridge.delivery.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.entity.EvidenceVersion;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.casefile.repository.EvidenceRepository;
import com.aidbridge.delivery.dto.request.DeliveryRequest;
import com.aidbridge.delivery.dto.response.DeliveryResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.entity.DeliveryEvidenceLink;
import com.aidbridge.delivery.entity.DeliveryItem;
import com.aidbridge.delivery.repository.DeliveryEvidenceLinkRepository;
import com.aidbridge.delivery.repository.DeliveryItemRepository;
import com.aidbridge.delivery.repository.DeliveryRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.shared.util.ChainRefs;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final DeliveryRepository deliveries;
    private final AuditEventRepository auditEvents;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;
    private final CampaignItemRepository campaignItems;
    private final EvidenceRepository evidence;
    private final DeliveryItemRepository deliveryItems;
    private final DeliveryEvidenceLinkRepository evidenceLinks;
    private final BlockchainJobRepository jobs;

    @Transactional
    public Result<DeliveryResponse> record(
            Authentication authentication, UUID caseId, String key, DeliveryRequest request
    ) {
        var memberships = organizations.currentMemberships(authentication).stream()
                .filter(membership -> !membership.getRoles().isEmpty())
                .toList();
        var orgIds = memberships.stream()
                .map(membership -> membership.getOrganization().getId())
                .toList();
        if (orgIds.isEmpty()) {
            throw BusinessException.missing();
        }

        AidCase aidCase = cases.lockScoped(caseId, orgIds)
                .orElseThrow(BusinessException::missing);
        UUID orgId = aidCase.getOrganization().getId();
        var membership = memberships.stream()
                .filter(candidate -> orgId.equals(candidate.getOrganization().getId()))
                .findFirst()
                .orElseThrow(BusinessException::missing);
        if (!membership.getRoles().contains(OrganizationMembership.Role.VOLUNTEER)) {
            throw new AccessDeniedException("Access is denied");
        }

        UUID recorderId = membership.getUser().getId();
        return idempotency.execute(
                recorderId, orgId, "POST:/api/cases/" + caseId + "/deliveries",
                key, request, DeliveryResponse.class,
                () -> recordNew(aidCase, orgId, recorderId, request)
        );
    }

    private Result<DeliveryResponse> recordNew(
            AidCase aidCase, UUID orgId, UUID recorderId, DeliveryRequest request
    ) {
        UUID caseId = aidCase.getId();
        Campaign campaign = campaigns.lockByCaseAndOrganization(caseId, orgId)
                .orElseThrow(BusinessException::state);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) {
            throw BusinessException.missing();
        }
        if (campaigns.hasActiveDispute(caseId)) {
            throw BusinessException.disputeOpen();
        }
        if (aidCase.getStatus() != AidCase.Status.IN_PROGRESS
                || campaign.getStatus() != Campaign.Status.PUBLISHED) {
            throw BusinessException.state();
        }
        if (!"FAILED".equals(request.outcome())) {
            validateHandoverTime(campaign, request.handedOverAt());
            validateAvailableStock(campaign.getId(), request);
            List<UUID> evidenceIds = resolveEvidenceSnapshot(caseId, orgId, request.evidenceVersionIds());
            return recordDelivered(aidCase, campaign, orgId, recorderId, request, evidenceIds);
        }

        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Delivery delivery = deliveries.save(new Delivery(
                caseId, campaign.getId(), recorderId, Delivery.Outcome.FAILED,
                request.reason(), null, recordedAt
        ));
        UUID deliveryId = delivery.getId();

        String privatePayload = json.writeValueAsString(Map.of(
                "payloadVersion", 1,
                "deliveryId", deliveryId,
                "outcome", delivery.getOutcome().name(),
                "reason", delivery.getReason()
        ));
        AuditEvent event = auditEvents.save(new AuditEvent(
                orgId, caseId, recorderId, "DELIVERY", deliveryId,
                "DELIVERY_ATTEMPT_FAILED", deliveryId, privatePayload
        ));

        var response = new DeliveryResponse(
                deliveryId, caseId, recorderId, delivery.getOutcome().name(),
                "NOT_APPLICABLE", List.of(), List.of(), recordedAt,
                event.getId(), "NOT_APPLICABLE", null
        );
        return new Result<>(response, 201, "/api/deliveries/" + deliveryId, false);
    }

    private Result<DeliveryResponse> recordDelivered(
            AidCase aidCase, Campaign campaign, UUID orgId, UUID recorderId,
            DeliveryRequest request, List<UUID> evidenceIds
    ) {
        UUID caseId = aidCase.getId();
        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant handedOverAt = request.handedOverAt() == null
                ? null : request.handedOverAt().truncatedTo(ChronoUnit.MICROS);

        // Flush the header first: child rows reference its ID through composite foreign keys.
        Delivery delivery = deliveries.saveAndFlush(new Delivery(
                caseId, campaign.getId(), recorderId, Delivery.Outcome.DELIVERED,
                request.reason(), handedOverAt, recordedAt
        ));
        deliveryItems.saveAll(request.items().stream()
                .map(line -> new DeliveryItem(delivery, line.campaignItemId(), line.quantity()))
                .toList());
        // Immutable snapshot of the evidence versions shown at handover (BR-09).
        evidenceLinks.saveAll(evidenceIds.stream()
                .map(evidenceId -> new DeliveryEvidenceLink(delivery, evidenceId, recorderId, orgId))
                .toList());

        var lines = request.items().stream()
                .map(line -> new DeliveryResponse.Item(line.campaignItemId(), line.quantity()))
                .toList();
        UUID auditEventId = enqueueDelivered(orgId, caseId, recorderId, delivery, lines, evidenceIds);

        // PROVISIONAL (F02-A04): public audit route is not implemented yet.
        String auditUrl = "/api/public/cases/0x" + HexFormat.of().formatHex(aidCase.getPublicRef()) + "/audit";
        var response = new DeliveryResponse(
                delivery.getId(), caseId, recorderId, Delivery.Outcome.DELIVERED.name(),
                "PENDING", lines, evidenceIds, recordedAt, auditEventId, "PENDING_ON_CHAIN", auditUrl
        );
        return new Result<>(response, 201, "/api/deliveries/" + delivery.getId(), false);
    }

    /**
     * DELIVERY_RECORDED + one PENDING job in the caller's transaction. The case row lock taken in record()
     * is still held, so MAX(chain_sequence)+1 cannot race (D53). The handover note stays out of the payload.
     */
    private UUID enqueueDelivered(
            UUID orgId, UUID caseId, UUID recorderId, Delivery delivery,
            List<DeliveryResponse.Item> lines, List<UUID> evidenceIds
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("deliveryId", delivery.getId());
        payload.put("campaignId", delivery.getCampaignId());
        // PROVISIONAL: stable bytes32 deliveryRef for C10 (recordDelivery / flagDispute), as receiptRef in B08.
        payload.put("deliveryRef", "0x" + HexFormat.of().formatHex(ChainRefs.randomBytes32()));
        payload.put("handedOverAt", delivery.getHandedOverAt());
        payload.put("lines", lines);
        payload.put("evidenceVersionIds", evidenceIds);

        long nextSequence = jobs.maxChainSequence(caseId) + 1;
        AuditEvent event = auditEvents.save(new AuditEvent(
                orgId, caseId, recorderId, "DELIVERY", delivery.getId(),
                "DELIVERY_RECORDED", delivery.getId(), json.writeValueAsString(payload)
        ));
        jobs.saveAndFlush(new BlockchainJob(event, nextSequence));
        return event.getId();
    }

    /** PROVISIONAL window, same rule as R01 receivedAt: not before publish, at most 5 minutes ahead. */
    private static void validateHandoverTime(Campaign campaign, Instant handedOverAt) {
        if (handedOverAt == null) {
            return;
        }
        Instant start = campaign.getPublishedAt() == null ? campaign.getCreatedAt() : campaign.getPublishedAt();
        if (handedOverAt.isBefore(start) || handedOverAt.isAfter(Instant.now().plusSeconds(300))) {
            throw BusinessException.invalid();
        }
    }

    private void validateAvailableStock(UUID campaignId, DeliveryRequest request) {
        var itemIds = request.items().stream()
                .map(DeliveryRequest.Line::campaignItemId)
                .toList();
        var lockedItems = campaignItems.lockByIds(campaignId, itemIds);
        if (lockedItems.size() != itemIds.size()) {
            throw BusinessException.state();
        }
        var receivedByItem = campaignItems.progressTotals(itemIds).stream()
                .collect(Collectors.toMap(
                        CampaignItemRepository.ItemProgress::getItemId,
                        CampaignItemRepository.ItemProgress::getReceivedQty));
        var deliveredByItem = deliveries.deliveredTotals(campaignId, itemIds).stream()
                .collect(Collectors.toMap(
                        DeliveryRepository.DeliveredTotal::getItemId,
                        DeliveryRepository.DeliveredTotal::getQuantity));
        for (var line : request.items()) {
            UUID itemId = line.campaignItemId();
            long available = receivedByItem.getOrDefault(itemId, 0L)
                    - deliveredByItem.getOrDefault(itemId, 0L);
            if (line.quantity() > available) {
                throw BusinessException.quantityExceeded();
            }
        }
    }

    private List<UUID> resolveEvidenceSnapshot(
            UUID caseId, UUID orgId, List<UUID> requestedIds
    ) {
        var allowedIds = evidence.findCurrent(caseId, orgId).stream()
                .map(EvidenceVersion::getId)
                .collect(Collectors.toSet());

        if (!allowedIds.containsAll(requestedIds)) {
            throw BusinessException.invalid();
        }

        return List.copyOf(requestedIds);
    }
}