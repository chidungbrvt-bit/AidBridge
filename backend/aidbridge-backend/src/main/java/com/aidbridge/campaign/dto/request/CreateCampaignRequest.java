package com.aidbridge.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotNull UUID caseId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 2000) String publicSummary
) {
    public CreateCampaignRequest {
        title = normalize(title);
        publicSummary = normalize(publicSummary);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
