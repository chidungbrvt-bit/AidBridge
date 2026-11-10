package com.aidbridge.casefile.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aidbridge.organization.entity.Organization;
import com.aidbridge.user.entity.User;
import jakarta.validation.Validation;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BeneficiaryTest {

    @Test
    void acceptsSimulatedHouseholdAtTextLimitsWithoutPrivateData() {
        Beneficiary beneficiary = validBeneficiary();
        ReflectionTestUtils.setField(beneficiary, "referenceCode", "R".repeat(50));
        ReflectionTestUtils.setField(beneficiary, "displayLabel", "L".repeat(150));

        assertThat(invalidFields(beneficiary)).isEmpty();
    }

    @Test
    void rejectsMissingOrganizationCreatorAndBeneficiaryType() {
        Beneficiary beneficiary = validBeneficiary();
        ReflectionTestUtils.setField(beneficiary, "organization", null);
        ReflectionTestUtils.setField(beneficiary, "createdBy", null);
        ReflectionTestUtils.setField(beneficiary, "beneficiaryType", null);

        assertThat(invalidFields(beneficiary))
                .containsExactlyInAnyOrder("organization", "createdBy", "beneficiaryType");
    }

    @Test
    void rejectsBlankReferenceAndDisplayLabel() {
        Beneficiary beneficiary = validBeneficiary();
        ReflectionTestUtils.setField(beneficiary, "referenceCode", " ");
        ReflectionTestUtils.setField(beneficiary, "displayLabel", " ");

        assertThat(invalidFields(beneficiary))
                .containsExactlyInAnyOrder("referenceCode", "displayLabel");
    }

    @Test
    void rejectsReferenceAndDisplayLabelBeyondSchemaLimits() {
        Beneficiary beneficiary = validBeneficiary();
        ReflectionTestUtils.setField(beneficiary, "referenceCode", "R".repeat(51));
        ReflectionTestUtils.setField(beneficiary, "displayLabel", "L".repeat(151));

        assertThat(invalidFields(beneficiary))
                .containsExactlyInAnyOrder("referenceCode", "displayLabel");
    }

    private Beneficiary validBeneficiary() {
        Beneficiary beneficiary = new Beneficiary();
        ReflectionTestUtils.setField(beneficiary, "organization",
                new Organization("Demo Organization", "Simulated organization"));
        ReflectionTestUtils.setField(beneficiary, "createdBy",
                new User("Demo Staff", "staff@example.com", "encoded-password"));
        ReflectionTestUtils.setField(beneficiary, "referenceCode", "BEN-0001");
        ReflectionTestUtils.setField(beneficiary, "displayLabel", "Demo Household A");
        return beneficiary;
    }

    private Set<String> invalidFields(Beneficiary beneficiary) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(beneficiary).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
        }
    }
}
