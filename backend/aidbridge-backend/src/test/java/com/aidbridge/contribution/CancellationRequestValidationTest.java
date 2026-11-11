package com.aidbridge.contribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.contribution.dto.request.CancellationRequest;
import com.aidbridge.contribution.dto.request.CancellationRequest.Line;
import jakarta.validation.Validation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancellationRequestValidationTest {

    @Test
    void acceptsAValidRequestAndTrimsTheReason() {
        var request = new CancellationRequest(List.of(new Line(UUID.randomUUID(), 1)), "  Stock arrived elsewhere  ");
        assertThat(request.reason()).isEqualTo("Stock arrived elsewhere");
        assertThat(violations(request)).isEmpty();
        assertThat(violations(new CancellationRequest(lines(), "R".repeat(500)))).isEmpty();
    }

    @Test
    void reasonIsRequiredAndBounded() {
        assertThat(violations(new CancellationRequest(lines(), null))).containsExactly("reason");
        assertThat(violations(new CancellationRequest(lines(), "   "))).containsExactly("reason");
        assertThat(violations(new CancellationRequest(lines(), "R".repeat(501)))).containsExactly("reason");
    }

    @Test
    void reasonMustNotCarryMarkupUrlsOrNulBytes() {
        for (String unsafe : new String[] {"<b>late</b>", "a > b", "see https://example.com", "a\0b"}) {
            assertThat(violations(new CancellationRequest(lines(), unsafe))).as(unsafe).containsExactly("safeReason");
        }
    }

    @Test
    void linesMustBeDistinctAndWithinBounds() {
        UUID id = UUID.randomUUID();
        assertThat(violations(new CancellationRequest(List.of(new Line(id, 1), new Line(id, 1)), "Reason")))
                .containsExactly("distinctContributionItems");
        for (Integer quantity : new Integer[] {0, 1_000_001, null}) {
            assertThat(violations(new CancellationRequest(List.of(new Line(id, quantity)), "Reason")))
                    .as("quantity %s", quantity).containsExactly("items[0].quantity");
        }
        assertThat(violations(new CancellationRequest(List.of(), "Reason"))).containsExactly("items");
    }

    private static List<Line> lines() {
        return List.of(new Line(UUID.randomUUID(), 1));
    }

    private static Set<String> violations(Object object) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Set<String> fields = new HashSet<>();
            factory.getValidator().validate(object).forEach(v -> fields.add(v.getPropertyPath().toString()));
            return fields;
        }
    }
}
