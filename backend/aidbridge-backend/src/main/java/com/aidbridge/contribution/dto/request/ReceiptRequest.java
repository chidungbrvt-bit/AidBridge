package com.aidbridge.contribution.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ReceiptRequest(
        @NotNull Instant receivedAt,
        @NotNull @Size(min = 1) List<@NotNull @Valid Line> items,
        @Size(max = 500) String note
) {
    public ReceiptRequest {
        if (note != null) {
            note = note.strip();
            if (note.isEmpty()) note = null;
        }
    }

    public record Line(
            @NotNull UUID contributionItemId,
            @NotNull @Positive @JsonDeserialize(using = IntegerQuantityDeserializer.class) Integer quantity
    ) {
    }

    @JsonIgnore
    @AssertTrue
    public boolean isDistinctContributionItems() {
        if (items == null) return true;
        var seen = new HashSet<UUID>();
        return items.stream().filter(Objects::nonNull).map(Line::contributionItemId)
                .filter(Objects::nonNull).allMatch(seen::add);
    }

    @JsonIgnore
    @AssertTrue
    public boolean isSafeNote() {
        return note == null || !(note.contains("<") || note.contains(">") || note.contains("://")
                || note.indexOf('\0') >= 0);
    }

    /** Reject numeric strings and fractions at the R01 boundary without changing other APIs. */
    public static final class IntegerQuantityDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}
