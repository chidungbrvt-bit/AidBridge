package com.aidbridge.delivery.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record DeliveryRequest(
        @NotNull @Pattern(regexp = "DELIVERED|FAILED") String outcome,
        Instant handedOverAt,
        @NotNull List<@NotNull @Valid Line> items,
        @NotNull List<@NotNull UUID> evidenceVersionIds,
        @Size(max = 2000) String reason
) {
    public DeliveryRequest {
        if (reason != null) {
            reason = reason.strip();
            if (reason.isEmpty()) {
                reason = null;
            }
        }
    }

    public record Line(
            @NotNull UUID campaignItemId,
            @NotNull @Positive @Max(1_000_000)
            @JsonDeserialize(using = IntegerQuantityDeserializer.class)
            Integer quantity
    ) {
    }

    @JsonIgnore
    @AssertTrue
    public boolean isOutcomePayloadValid() {
        if (items == null || evidenceVersionIds == null) {
            return true; // Required fields are checked by @NotNull.
        }
        if ("FAILED".equals(outcome)) {
            return items.isEmpty() && evidenceVersionIds.isEmpty()
                    && reason != null && handedOverAt == null;
        }
        if ("DELIVERED".equals(outcome)) {
            return !items.isEmpty() && !evidenceVersionIds.isEmpty();
        }
        return true; // The outcome field is checked separately.
    }

    @JsonIgnore
    @AssertTrue
    public boolean isDistinctCampaignItems() {
        if (items == null) {
            return true;
        }
        Set<UUID> seen = new HashSet<>();
        for (Line line : items) {
            if (line != null && line.campaignItemId() != null
                    && !seen.add(line.campaignItemId())) {
                return false;
            }
        }
        return true;
    }

    @JsonIgnore
    @AssertTrue
    public boolean isDistinctEvidenceVersions() {
        return evidenceVersionIds == null
                || new HashSet<>(evidenceVersionIds).size() == evidenceVersionIds.size();
    }

    @JsonIgnore
    @AssertTrue
    public boolean isPlainTextReason() {
        return reason == null
                || (reason.indexOf('<') < 0 && reason.indexOf('>') < 0
                && reason.indexOf('\0') < 0);
    }

    public static final class IntegerQuantityDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}