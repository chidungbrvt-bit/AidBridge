package com.aidbridge.delivery.service;

import com.aidbridge.delivery.dto.response.DeliveryReviewResponse;
import com.aidbridge.delivery.dto.response.DisputeSummaryResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.entity.DeliveryConfirmation;
import com.aidbridge.delivery.entity.Dispute;
import com.aidbridge.delivery.repository.DeliveryConfirmationRepository;
import com.aidbridge.delivery.repository.DeliveryEvidenceLinkRepository;
import com.aidbridge.delivery.repository.DeliveryItemRepository;
import com.aidbridge.delivery.repository.DeliveryRepository;
import com.aidbridge.delivery.repository.DeliveryVoidRepository;
import com.aidbridge.delivery.repository.DisputeRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryReviewQueryService {

    private final DeliveryRepository deliveries;
    private final DeliveryItemRepository deliveryItems;
    private final DeliveryEvidenceLinkRepository evidenceLinks;
    private final DeliveryConfirmationRepository confirmations;
    private final DisputeRepository disputes;
    private final DeliveryVoidRepository deliveryVoids;

    public record Snapshot(
            List<DeliveryReviewResponse> deliveries,
            List<DisputeSummaryResponse> disputes
    ) {
        public Snapshot {
            deliveries = List.copyOf(deliveries);
            disputes = List.copyOf(disputes);
        }
    }

    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public Snapshot readForAuthorizedCase(
            UUID caseId,
            UUID organizationId
    ) {
        var storedDeliveries = deliveries.findAllForCaseScoped(caseId, organizationId);
        var storedItems = deliveryItems.findAllForCaseScoped(caseId, organizationId);
        var storedEvidence = evidenceLinks.findAllForCaseScoped(caseId, organizationId);
        var storedConfirmations = confirmations.findAllForCaseScoped(caseId, organizationId);
        var storedDisputes = disputes.findAllForCaseScoped(caseId, organizationId);

        Set<UUID> voidedIds = new HashSet<>(
                deliveryVoids.findVoidedDeliveryIdsForCaseScoped(
                        caseId,
                        organizationId
                )
        );

        Map<UUID, List<DeliveryReviewResponse.Item>> itemsByDelivery =
                new HashMap<>();

        for (var item : storedItems) {
            itemsByDelivery
                    .computeIfAbsent(
                            item.getDeliveryId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(new DeliveryReviewResponse.Item(
                            item.getCampaignItemId(),
                            item.getQuantity()
                    ));
        }

        Map<UUID, Set<UUID>> evidenceByDelivery = new HashMap<>();

        for (var link : storedEvidence) {
            evidenceByDelivery
                    .computeIfAbsent(
                            link.getDeliveryId(),
                            ignored -> new LinkedHashSet<>()
                    )
                    .add(link.getEvidenceVersionId());
        }

        Map<UUID, List<DeliveryConfirmation>> confirmationsByDelivery =
                new HashMap<>();

        for (var confirmation : storedConfirmations) {
            confirmationsByDelivery
                    .computeIfAbsent(
                            confirmation.getDeliveryId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(confirmation);
        }

        Map<UUID, List<Dispute>> disputesByDelivery = new HashMap<>();

        for (var dispute : storedDisputes) {
            if (dispute.getDeliveryId() != null) {
                disputesByDelivery
                        .computeIfAbsent(
                                dispute.getDeliveryId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(dispute);
            }
        }

        List<DeliveryReviewResponse> deliveryResponses =
                storedDeliveries.stream()
                        .map(delivery -> new DeliveryReviewResponse(
                                delivery.getId(),
                                delivery.getRecorderId(),
                                delivery.getOutcome().name(),
                                reviewStatus(
                                        delivery,
                                        voidedIds,
                                        confirmationsByDelivery.getOrDefault(
                                                delivery.getId(),
                                                List.of()
                                        ),
                                        disputesByDelivery.getOrDefault(
                                                delivery.getId(),
                                                List.of()
                                        )
                                ),
                                itemsByDelivery.getOrDefault(
                                        delivery.getId(),
                                        List.of()
                                ),
                                List.copyOf(
                                        evidenceByDelivery.getOrDefault(
                                                delivery.getId(),
                                                Set.of()
                                        )
                                ),
                                delivery.getRecordedAt()
                        ))
                        .toList();

        List<DisputeSummaryResponse> disputeResponses =
                storedDisputes.stream()
                        .map(dispute -> new DisputeSummaryResponse(
                                dispute.getId(),
                                dispute.getDeliveryId(),
                                dispute.getStatus().name(),
                                dispute.getReason(),
                                dispute.getCreatedAt()
                        ))
                        .toList();

        return new Snapshot(deliveryResponses, disputeResponses);
    }

    private String reviewStatus(
            Delivery delivery,
            Set<UUID> voidedIds,
            List<DeliveryConfirmation> history,
            List<Dispute> deliveryDisputes
    ) {
        if (voidedIds.contains(delivery.getId())) {
            return "VOIDED";
        }

        if (delivery.getOutcome() == Delivery.Outcome.FAILED) {
            return "NOT_APPLICABLE";
        }

        boolean unresolvedDispute = deliveryDisputes.stream()
                .anyMatch(dispute ->
                        dispute.getStatus() == Dispute.Status.OPEN
                                || dispute.getStatus()
                                == Dispute.Status.UNDER_REVIEW
                );

        if (unresolvedDispute) {
            return "REQUIRES_REVIEW";
        }

        boolean confirmed = history.stream()
                .anyMatch(confirmation ->
                        "CONFIRMED".equals(confirmation.getDecision())
                );

        if (confirmed) {
            return "CONFIRMED";
        }

        boolean legacyReviewWithoutDispute = deliveryDisputes.isEmpty()
                && history.stream().anyMatch(confirmation ->
                "NEEDS_REVIEW".equals(confirmation.getDecision())
                        || "REJECTED".equals(
                        confirmation.getDecision()
                )
        );

        if (legacyReviewWithoutDispute) {
            throw new IllegalStateException(
                    "Legacy delivery review requires explicit reconciliation."
            );
        }

        return "PENDING";
    }
}