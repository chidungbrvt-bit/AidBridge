package com.aidbridge.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.campaign.dto.request.CreateCampaignRequest;
import jakarta.validation.Validation;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignRequestValidationTest {

    @Test
    void acceptsValidRequestAndTrimsText() {
        var request = new CreateCampaignRequest(UUID.randomUUID(), "  Winter food support  ", "  Public summary  ");

        assertThat(request.title()).isEqualTo("Winter food support");
        assertThat(request.publicSummary()).isEqualTo("Public summary");
        assertThat(violations(request)).isEmpty();
    }

    @Test
    void rejectsMissingCaseId() {
        var request = new CreateCampaignRequest(null, "Title", "Summary");

        assertThat(violations(request)).containsExactly("caseId");
    }

    @Test
    void blankTextIsNormalizedToNullAndRejected() {
        var request = new CreateCampaignRequest(UUID.randomUUID(), "   ", "");

        assertThat(request.title()).isNull();
        assertThat(request.publicSummary()).isNull();
        assertThat(violations(request)).containsExactlyInAnyOrder("title", "publicSummary");
    }

    @Test
    void enforcesLengthBoundsOnTitleAndPublicSummary() {
        var atLimit = new CreateCampaignRequest(UUID.randomUUID(), "T".repeat(160), "S".repeat(2000));
        assertThat(violations(atLimit)).isEmpty();

        var overLimit = new CreateCampaignRequest(UUID.randomUUID(), "T".repeat(161), "S".repeat(2001));
        assertThat(violations(overLimit)).containsExactlyInAnyOrder("title", "publicSummary");
    }

    private Set<String> violations(Object object) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Set<String> fields = new HashSet<>();
            factory.getValidator().validate(object)
                    .forEach(v -> fields.add(v.getPropertyPath().toString()));
            return fields;
        }
    }
}