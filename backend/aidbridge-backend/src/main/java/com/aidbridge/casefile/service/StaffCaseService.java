package com.aidbridge.casefile.service;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse.Item;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse.OutstandingCommitment;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse.ReviewCampaign;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.repository.ContributionItemRepository;
import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.delivery.service.DeliveryReviewQueryService;
import com.aidbridge.delivery.service.GoodsTotalsQueryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffCaseService {

    private final CaseService cases;
    private final ContributionItemRepository contributionItems;
    private final CampaignRepository campaigns;
    private final GoodsTotalsQueryService goodsTotals;
    private final DeliveryReviewQueryService deliveryReviews;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public StaffCaseDetailResponse get(
            Authentication actor,
            UUID caseId
    ) {
        // Authorize private case access before reading related data.
        var detail = cases.get(actor, caseId);
        UUID organizationId = detail.organizationId();

        Map<UUID, OutstandingCommitment> outstanding =
                new LinkedHashMap<>();

        for (var row : contributionItems.findOutstandingForCase(
                caseId,
                organizationId,
                List.of(
                        Contribution.Status.PLEDGED,
                        Contribution.Status.PARTIALLY_RECEIVED
                )
        )) {
            var commitment = outstanding.computeIfAbsent(
                    row.getContributionId(),
                    id -> new OutstandingCommitment(
                            id,
                            row.getStatus().name(),
                            row.getPledgedAt(),
                            new ArrayList<>()
                    )
            );

            commitment.items().add(new Item(
                    row.getContributionItemId(),
                    row.getCampaignItemId(),
                    row.getItemName(),
                    row.getUnit(),
                    row.getPledgedQty(),
                    row.getReceivedQty(),
                    row.getOutstandingQty()
            ));
        }

        var campaign = campaigns.findStaffCampaign(caseId, organizationId);
        UUID publishedCampaignId = campaign.filter(entry -> entry.getStatus() == Campaign.Status.PUBLISHED)
                .map(CampaignRepository.StaffCampaign::getId).orElse(null);
        var reviewCampaign = campaign.filter(entry -> entry.getStatus() != Campaign.Status.DRAFT)
                .map(entry -> new ReviewCampaign(entry.getId(), entry.getStatus().name())).orElse(null);
        List<ConfirmationResponse.GoodsTotal> totals = campaign
                .map(entry -> goodsTotals.readForAuthorizedCampaign(organizationId, entry.getId()))
                .orElseGet(List::of);

        var reviewSnapshot = deliveryReviews.readForAuthorizedCase(
                caseId,
                organizationId
        );

        return new StaffCaseDetailResponse(
                detail,
                publishedCampaignId,
                reviewCampaign,
                new ArrayList<>(outstanding.values()),
                totals,
                reviewSnapshot.deliveries(),
                reviewSnapshot.disputes()
        );
    }
}