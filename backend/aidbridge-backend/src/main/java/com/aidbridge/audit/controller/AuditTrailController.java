package com.aidbridge.audit.controller;

import com.aidbridge.audit.dto.response.AuditTrailPage;
import com.aidbridge.audit.dto.response.InternalAuditEventResponse;
import com.aidbridge.audit.service.AuditTrailService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuditTrailController {
    private final AuditTrailService service;

    /** T01: internal audit trail of one case, chronological, with the live chain status of each event. */
    @GetMapping("/api/cases/{id}/audit-trail")
    public ResponseEntity<AuditTrailPage<InternalAuditEventResponse>> caseTrail(
            Authentication auth,
            @PathVariable("id") UUID caseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.caseTrail(auth, caseId, page, size));
    }
}
