package com.aidbridge.casefile.dto.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record EvidenceVersionRequest(@NotNull @PositiveOrZero Long expectedCaseVersion,
        UUID evidenceFileId, @NotBlank @Size(max=120) String fixtureKey, UUID supersedesVersionId) { }
