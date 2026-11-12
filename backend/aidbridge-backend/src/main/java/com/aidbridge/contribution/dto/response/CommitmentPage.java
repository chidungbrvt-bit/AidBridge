package com.aidbridge.contribution.dto.response;

import java.util.List;

/** Same page shape as CampaignPage and the F07 mock (Page<CommitmentResponse>). */
public record CommitmentPage(
        List<CommitmentResponse> items,
        int page,
        int size,
        long totalElements
) {
}
