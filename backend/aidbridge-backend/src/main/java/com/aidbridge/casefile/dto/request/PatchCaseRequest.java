package com.aidbridge.casefile.dto.request;
import com.aidbridge.casefile.entity.AidCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record PatchCaseRequest(@NotNull @PositiveOrZero Long expectedVersion,
        @Size(min=1,max=160) String internalTitle, @Size(max=160) String publicTitle,
        @Size(max=2000) String publicSummary, @Size(min=1,max=160) String approximateArea,
        AidCase.Priority priority,
        @Size(min=1,max=20) List<@NotNull @Valid NeedInput> needs) {
    public PatchCaseRequest {
        internalTitle = trim(internalTitle); publicTitle = trim(publicTitle);
        publicSummary = trim(publicSummary); approximateArea = trim(approximateArea);
    }
    private static String trim(String value) { return value == null ? null : value.strip(); }
}
