package com.aidbridge.casefile.dto.request;
import com.aidbridge.casefile.entity.AidCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record CreateCaseRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max=120) String beneficiaryAlias,
        @NotBlank @Size(max=160) String approximateArea,
        @NotBlank @Size(max=160) String internalTitle,
        @Size(max=160) String publicTitle,
        @Size(max=2000) String publicSummary,
        @NotNull AidCase.Priority priority,
        @NotNull @Valid ConsentInput consent,
        @NotNull @Size(min=1,max=20) List<@NotNull @Valid NeedInput> needs,
        @NotNull @Size(min=1,max=10) List<@NotBlank @Size(max=120) String> evidenceFixtureKeys,
        UUID supersedesCaseId) {
    public CreateCaseRequest {
        beneficiaryAlias = trim(beneficiaryAlias); approximateArea = trim(approximateArea);
        internalTitle = trim(internalTitle); publicTitle = trim(publicTitle); publicSummary = trim(publicSummary);
    }
    private static String trim(String value) { return value == null ? null : value.strip(); }
}
