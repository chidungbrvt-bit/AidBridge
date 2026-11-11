package com.aidbridge.casefile.dto.response;
import java.time.Instant;
import java.util.UUID;
public record EvidenceVersionResponse(UUID id, UUID evidenceFileId, int versionNo,
        String mimeType, long byteSize, String sha256, Instant createdAt) { }
