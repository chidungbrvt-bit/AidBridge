package com.aidbridge.contribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.contribution.dto.request.CommitmentRequest;
import com.aidbridge.contribution.dto.request.CommitmentRequest.Line;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CommitmentRequestValidationTest {

    @Test
    void acceptsDistinctLinesAtTheQuantityBounds() {
        var request = new CommitmentRequest(List.of(
                new Line(UUID.randomUUID(), 1), new Line(UUID.randomUUID(), 1_000_000)));
        assertThat(violations(request)).isEmpty();
    }

    @Test
    void acceptsExactlyFiftyLinesButNotFiftyOne() {
        assertThat(violations(new CommitmentRequest(lines(50)))).isEmpty();
        assertThat(violations(new CommitmentRequest(lines(51)))).containsExactly("items");
    }

    @Test
    void rejectsMissingOrEmptyItems() {
        assertThat(violations(new CommitmentRequest(null))).containsExactly("items");
        assertThat(violations(new CommitmentRequest(List.of()))).containsExactly("items");
    }

    @Test
    void rejectsQuantitiesOutsideOneToOneMillionAndMissingFields() {
        UUID id = UUID.randomUUID();
        for (Integer quantity : new Integer[] {0, -1, 1_000_001, null}) {
            assertThat(violations(new CommitmentRequest(List.of(new Line(id, quantity)))))
                    .as("quantity %s", quantity)
                    .containsExactly("items[0].quantity");
        }
        assertThat(violations(new CommitmentRequest(List.of(new Line(null, 1)))))
                .containsExactly("items[0].campaignItemId");
    }

    @Test
    void rejectsTheSameCampaignItemTwice() {
        UUID id = UUID.randomUUID();
        var request = new CommitmentRequest(List.of(new Line(id, 1), new Line(id, 2)));
        assertThat(request.isDistinctCampaignItems()).isFalse();
        assertThat(violations(request)).containsExactly("distinctCampaignItems");
    }

    private static List<Line> lines(int count) {
        var lines = new ArrayList<Line>();
        IntStream.range(0, count).forEach(i -> lines.add(new Line(UUID.randomUUID(), 1)));
        return lines;
    }

    private static Set<String> violations(Object object) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Set<String> fields = new HashSet<>();
            factory.getValidator().validate(object).forEach(v -> fields.add(v.getPropertyPath().toString()));
            return fields;
        }
    }
}
