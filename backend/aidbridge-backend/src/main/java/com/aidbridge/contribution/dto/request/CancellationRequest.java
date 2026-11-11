package com.aidbridge.contribution.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CancellationRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Line> items,
        // Required (Q9); stored only in the private audit payload, never in a public DTO.
        @NotBlank @Size(max = 500) String reason
) {
    public CancellationRequest {
        reason = reason == null ? null : reason.strip();
    }

    public record Line(
            @NotNull UUID contributionItemId,
            @NotNull @Min(1) @Max(1_000_000) Integer quantity
    ) {
    }

    @JsonIgnore
    @AssertTrue
    public boolean isDistinctContributionItems() {
        if (items == null) {
            return true;
        }
        var seen = new HashSet<UUID>();
        return items.stream().filter(Objects::nonNull).map(Line::contributionItemId)
                .filter(Objects::nonNull).allMatch(seen::add);
    }

    /** Same text rules as CampaignService.sanitize: no markup, no URLs, no NUL bytes. */
    @JsonIgnore
    @AssertTrue
    public boolean isSafeReason() {
        return reason == null || !(reason.contains("<") || reason.contains(">")
                || reason.contains("://") || reason.indexOf('\0') >= 0);
    }
}
