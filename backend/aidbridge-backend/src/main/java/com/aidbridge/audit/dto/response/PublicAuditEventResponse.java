package com.aidbridge.audit.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * T02 item. A role label replaces any account identity; no entity id, case id, organization id, evidence,
 * consent, manifest, salt or private payload is part of this DTO.
 */
public record PublicAuditEventResponse(
        UUID eventId,
        String type,
        Instant occurredAt,
        String publicActorRole,
        String logicalEventId,
        PublicBlockchainStatusResponse blockchain) {
}
