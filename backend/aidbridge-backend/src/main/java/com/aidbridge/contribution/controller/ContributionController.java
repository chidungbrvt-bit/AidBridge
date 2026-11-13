package com.aidbridge.contribution.controller;

import com.aidbridge.contribution.dto.request.CancellationRequest;
import com.aidbridge.contribution.dto.request.CommitmentRequest;
import com.aidbridge.contribution.dto.request.ReceiptRequest;
import com.aidbridge.contribution.dto.response.CommitmentPage;
import com.aidbridge.contribution.dto.response.CommitmentResponse;
import com.aidbridge.contribution.dto.response.ReceiptResponse;
import com.aidbridge.contribution.service.ContributionService;
import com.aidbridge.contribution.service.GoodsReceiptService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The contribution endpoints live under /api/campaigns, /api/me and /api/commitments.
@RestController
@RequiredArgsConstructor
public class ContributionController {
    private final ContributionService service;
    private final GoodsReceiptService receipts;

    @PostMapping("/api/campaigns/{id}/commitments")
    public ResponseEntity<CommitmentResponse> pledge(
            Authentication authentication,
            @PathVariable("id") UUID campaignId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CommitmentRequest request
    ) {
        return toResponse(service.pledge(authentication, campaignId, idempotencyKey, request));
    }

    @GetMapping("/api/me/commitments")
    public ResponseEntity<CommitmentPage> listMine(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listMine(authentication, page, size));
    }

    @PostMapping("/api/commitments/{id}/cancellations")
    public ResponseEntity<CommitmentResponse> cancel(
            Authentication authentication,
            @PathVariable("id") UUID contributionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CancellationRequest request
    ) {
        return toResponse(service.cancel(authentication, contributionId, idempotencyKey, request));
    }

    @PostMapping("/api/commitments/{id}/receipts")
    public ResponseEntity<ReceiptResponse> receive(
            Authentication authentication,
            @PathVariable("id") UUID contributionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReceiptRequest request
    ) {
        return toResponse(receipts.receive(authentication, contributionId, idempotencyKey, request));
    }

    private static <T> ResponseEntity<T> toResponse(Result<T> result) {
        var builder = ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore());
        if (result.location() != null) {
            builder.location(URI.create(result.location()));
        }
        if (result.replayed()) {
            builder.header("Idempotent-Replayed", "true");
        }
        return builder.body(result.body());
    }
}
