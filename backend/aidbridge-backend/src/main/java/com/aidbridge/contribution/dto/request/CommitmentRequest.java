package com.aidbridge.contribution.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CommitmentRequest(
        // 50 is a protective limit (Q6), not a business rule; easy to change.
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Line> items
) {
    public record Line(
            @NotNull UUID campaignItemId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity
    ) {
    }

    /** One line per campaign item; duplicates are a malformed request, not a business conflict. */
    @JsonIgnore
    @AssertTrue
    public boolean isDistinctCampaignItems() {
        if (items == null) {
            return true;
        }
        var seen = new HashSet<UUID>();
        return items.stream().filter(Objects::nonNull).map(Line::campaignItemId)
                .filter(Objects::nonNull).allMatch(seen::add);
    }
}
