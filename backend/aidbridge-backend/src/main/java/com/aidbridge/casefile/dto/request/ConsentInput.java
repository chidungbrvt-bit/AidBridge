package com.aidbridge.casefile.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record ConsentInput(@NotNull Basis basis, @NotBlank @Size(max=200) String scope,
                           @NotNull @PastOrPresent Instant capturedAt, @NotNull Boolean publicSummaryAllowed) {
    public enum Basis { CONSENT, REPRESENTATIVE }
    public ConsentInput { scope = scope == null ? null : scope.strip(); }
}
