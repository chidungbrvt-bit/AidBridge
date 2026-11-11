package com.aidbridge.casefile.dto.request;
import jakarta.validation.constraints.*;

public record NeedInput(@NotBlank @Size(max=120) String name,
                        @NotBlank @Size(max=40) String unit,
                        @NotNull @Min(1) @Max(1_000_000) Integer quantity) {
    public NeedInput { name = name == null ? null : name.strip(); unit = unit == null ? null : unit.strip(); }
}
