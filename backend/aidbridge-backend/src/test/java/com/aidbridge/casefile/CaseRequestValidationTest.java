package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.*;
import com.aidbridge.casefile.dto.request.*;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.verification.dto.request.DecisionRequest;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class CaseRequestValidationTest {
    @Test void acceptsTwoNeedLinesAndExplicitConsentWithPublicSummaryDisabled() {
        assertThat(violations(valid())).isEmpty();
    }
    @Test void rejectsMissingConsentEvidenceAndVersion() {
        var r=valid();
        var missing=new CreateCaseRequest(r.organizationId(),r.beneficiaryAlias(),r.approximateArea(),r.internalTitle(),null,null,
                r.priority(),null,List.of(),List.of(),null);
        assertThat(violations(missing)).contains("consent","needs","evidenceFixtureKeys");
        assertThat(violations(new PatchCaseRequest(null,"Title",null,null,null,null,null))).contains("expectedVersion");
    }
    @Test void rejectsNegativeQuantityOversizedUnitAndFutureConsent() {
        assertThat(violations(new NeedInput("Food","U".repeat(41),-1))).contains("unit","quantity");
        assertThat(violations(new ConsentInput(ConsentInput.Basis.CONSENT,"Scope",Instant.now().plusSeconds(3600),false))).contains("capturedAt");
    }
    @Test void omittedConsentFlagIsNotSilentlyTreatedAsFalse() {
        assertThat(violations(new ConsentInput(ConsentInput.Basis.CONSENT,"Scope",Instant.now().minusSeconds(60),null))).contains("publicSummaryAllowed");
    }
    @Test void decisionRequestNormalizesBlankReasonAndValidatesBounds() {
        var normalized = new DecisionRequest(0L, "   ", List.of(UUID.randomUUID()));
        assertThat(normalized.reason()).isNull();
        assertThat(violations(normalized)).isEmpty();

        var trimmed = new DecisionRequest(1L, "  Missing signature  ", List.of(UUID.randomUUID()));
        assertThat(trimmed.reason()).isEqualTo("Missing signature");
        assertThat(violations(trimmed)).isEmpty();

        var invalid = new DecisionRequest(-1L, "R".repeat(2001), List.of());
        assertThat(violations(invalid)).contains("expectedVersion", "reason", "reviewedEvidenceVersionIds");
    }
    private CreateCaseRequest valid() {
        return new CreateCaseRequest(UUID.randomUUID(),"Demo Household A","Simulated district","Internal title",null,null,
                AidCase.Priority.NORMAL,new ConsentInput(ConsentInput.Basis.CONSENT,"Aid processing",Instant.now().minusSeconds(60),false),
                List.of(new NeedInput("Food","PACKAGE",2),new NeedInput("Hygiene","KIT",2)),List.of("household-note-v1"),null);
    }
    private Set<String> violations(Object object) {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            Set<String> fields=new HashSet<>(); factory.getValidator().validate(object).forEach(v->fields.add(v.getPropertyPath().toString()));return fields;
        }
    }
}
