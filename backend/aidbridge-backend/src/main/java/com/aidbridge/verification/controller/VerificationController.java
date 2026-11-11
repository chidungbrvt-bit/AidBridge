package com.aidbridge.verification.controller;

import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.verification.dto.request.DecisionRequest;
import com.aidbridge.verification.dto.response.DecisionResponse;
import com.aidbridge.verification.service.VerificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService service;

    @PostMapping("/{id}/verify")
    public ResponseEntity<DecisionResponse> approve(
            Authentication authentication,
            @PathVariable("id") UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DecisionRequest request
    ) {
        return toResponse(service.approve(authentication, caseId, key, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<DecisionResponse> reject(
            Authentication authentication,
            @PathVariable("id") UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DecisionRequest request
    ) {
        return toResponse(service.reject(authentication, caseId, key, request));
    }

    @PostMapping("/{id}/request-changes")
    public ResponseEntity<DecisionResponse> requestChanges(
            Authentication authentication,
            @PathVariable("id") UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DecisionRequest request
    ) {
        return toResponse(service.requestChanges(authentication, caseId, key, request));
    }

    private static ResponseEntity<DecisionResponse> toResponse(Result<DecisionResponse> result) {
        var response = ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore());
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.body());
    }
}