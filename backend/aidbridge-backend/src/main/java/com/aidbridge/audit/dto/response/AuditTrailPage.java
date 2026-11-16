package com.aidbridge.audit.dto.response;

import java.util.List;

/** Same page shape as CasePage: items in chronological order (occurredAt, then eventId). */
public record AuditTrailPage<T>(List<T> items, int page, int size, long totalElements) {
}
