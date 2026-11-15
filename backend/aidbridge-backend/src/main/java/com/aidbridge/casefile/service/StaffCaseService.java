package com.aidbridge.casefile.service;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse.Item;
import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse.OutstandingCommitment;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.repository.ContributionItemRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffCaseService {
    private final CaseService cases;
    private final ContributionItemRepository contributionItems;
    private final CampaignRepository campaigns;

    @Transactional(readOnly = true)
    public StaffCaseDetailResponse get(Authentication actor, UUID caseId) {
        var detail = cases.get(actor, caseId);
        Map<UUID, OutstandingCommitment> outstanding = new LinkedHashMap<>();
        for (var row : contributionItems.findOutstandingForCase(caseId, detail.organizationId(),
                List.of(Contribution.Status.PLEDGED, Contribution.Status.PARTIALLY_RECEIVED))) {
            var commitment = outstanding.computeIfAbsent(row.getContributionId(), id ->
                    new OutstandingCommitment(id, row.getStatus().name(), row.getPledgedAt(), new ArrayList<>()));
            commitment.items().add(new Item(row.getContributionItemId(), row.getCampaignItemId(),
                    row.getItemName(), row.getUnit(), row.getPledgedQty(), row.getReceivedQty(),
                    row.getOutstandingQty()));
        }
        UUID campaignId = campaigns.findScopedCampaignId(caseId, detail.organizationId(), Campaign.Status.PUBLISHED)
                .orElse(null);
        return new StaffCaseDetailResponse(detail, campaignId, new ArrayList<>(outstanding.values()));
    }
}
