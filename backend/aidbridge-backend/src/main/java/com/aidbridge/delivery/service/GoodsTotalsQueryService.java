package com.aidbridge.delivery.service;

import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.repository.DeliveryConfirmationRepository;
import com.aidbridge.delivery.repository.DeliveryRepository;
import com.aidbridge.shared.exception.BusinessException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoodsTotalsQueryService {

    private final CampaignItemRepository campaignItems;
    private final DeliveryRepository deliveries;
    private final DeliveryConfirmationRepository confirmations;

    @Transactional(
            propagation = Propagation.MANDATORY,
            readOnly = true
    )
    public List<ConfirmationResponse.GoodsTotal> readForAuthorizedCampaign(
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

        var deliveredByItem =
                deliveries.deliveredTotals(campaignId, itemIds)
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