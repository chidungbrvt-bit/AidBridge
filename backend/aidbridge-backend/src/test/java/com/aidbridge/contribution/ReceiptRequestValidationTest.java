package com.aidbridge.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.contribution.dto.request.ReceiptRequest;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class ReceiptRequestValidationTest {
    private static final UUID LINE = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void acceptsJsonIntegerAndTrimsOptionalNote() {
        var request = json.readValue(body("2"), ReceiptRequest.class);
        assertThat(request.items()).containsExactly(new ReceiptRequest.Line(LINE, 2));
        assertThat(request.note()).isEqualTo("Simulated receipt");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "\"2\"", "2147483648"})
    void rejectsNonIntegerTokensAndOverflow(String quantity) {
        assertThatThrownBy(() -> json.readValue(body(quantity), ReceiptRequest.class))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void validatesDistinctPositiveLinesAndSafeNote() {
        var request = new ReceiptRequest(Instant.now(),
                List.of(new ReceiptRequest.Line(LINE, 0), new ReceiptRequest.Line(LINE, 1)), "<tag>");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("items[0].quantity", "distinctContributionItems", "safeNote");
        }
    }

    private static String body(String quantity) {
        return """
                {"receivedAt":"2026-11-09T01:39:00Z",
                 "items":[{"contributionItemId":"%s","quantity":%s}],"note":"  Simulated receipt  "}
                """.formatted(LINE, quantity);
    }
}
