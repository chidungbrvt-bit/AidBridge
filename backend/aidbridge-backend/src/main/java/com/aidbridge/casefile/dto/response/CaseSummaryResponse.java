package com.aidbridge.casefile.dto.response;
import com.aidbridge.casefile.entity.AidCase;
import java.time.Instant;
import java.util.UUID;
public record CaseSummaryResponse(UUID id, UUID organizationId, String internalTitle,
        AidCase.Status status, AidCase.Priority priority, long version, Instant createdAt) { }
