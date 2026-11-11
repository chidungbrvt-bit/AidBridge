package com.aidbridge.campaign.dto.response;

import java.util.List;

public record CampaignPage(
        List<CampaignResponse> items,
        int page,
        int size,
        long totalElements
) {
}
