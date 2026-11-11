package com.aidbridge.casefile.dto.request;
import jakarta.validation.constraints.*;
public record ResubmitCaseRequest(@NotNull @PositiveOrZero Long expectedVersion) { }
