package com.aidbridge.audit.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * T01 item. Internal staff view: the actor is shown by name, but no private payload, manifest, salt,
 * signed bytes or receipt is ever part of this DTO.
 */
public record InternalAuditEventResponse(
        UUID eventId,
        String type,
        String entityType,
        Instant occurredAt,
        Actor actor,
        String logicalEventId,
        BlockchainStatusResponse blockchain) {

    public record Actor(UUID id, String fullName) {
    }
}
