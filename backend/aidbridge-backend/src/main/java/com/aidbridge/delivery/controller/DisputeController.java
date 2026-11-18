package com.aidbridge.delivery.controller;

import com.aidbridge.delivery.dto.request.DisputeRequest;
import com.aidbridge.delivery.dto.request.DonorDisputeRequest;
import com.aidbridge.delivery.dto.request.ResolveDisputeRequest;
import com.aidbridge.delivery.dto.response.DisputeResponse;
import com.aidbridge.delivery.dto.response.DisputeReviewResponse;
import com.aidbridge.delivery.dto.response.DonorDisputeResponse;
import com.aidbridge.delivery.service.DisputeService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DisputeController {

    private final DisputeService disputes;

    @PostMapping("/api/cases/{id}/disputes")
    public ResponseEntity<DisputeResponse> open(
            Authentication authentication,
            @PathVariable("id") UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DisputeRequest request
    ) {
        var result = disputes.open(authentication, caseId, key, request);

        var response = ResponseEntity.status(result.status())
                .location(URI.create(result.location()))
                .cacheControl(CacheControl.noStore());

        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }

        return response.body(result.body());
    }

    @PostMapping("/api/campaigns/{id}/disputes")
    public ResponseEntity<DonorDisputeResponse> openForDonor(
            Authentication authentication,
            @PathVariable("id") UUID campaignId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DonorDisputeRequest request
    ) {
        var result = disputes.openForDonor(authentication, campaignId, key, request);
        var response = ResponseEntity.status(result.status())
                .location(URI.create(result.location()))
                .cacheControl(CacheControl.noStore());

        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }

        return response.body(result.body());
    }

    @GetMapping("/api/disputes/{id}/review")
    public ResponseEntity<DisputeReviewResponse> review(Authentication authentication, @PathVariable("id") UUID disputeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(disputes.review(authentication, disputeId));
    }

    @GetMapping("/api/disputes/{id}/review/evidence-versions/{versionId}/content")
    public ResponseEntity<byte[]> reviewEvidence(
            Authentication authentication, @PathVariable("id") UUID disputeId,
            @PathVariable("versionId") UUID versionId
    ) {
        var content = disputes.reviewEvidenceContent(authentication, disputeId, versionId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length)
                .body(content.bytes());
    }

    @PostMapping("/api/disputes/{id}/resolve")
    public ResponseEntity<DisputeResponse> resolve(
            Authentication authentication,
            @PathVariable("id") UUID disputeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        var result = disputes.resolve(authentication, disputeId, key, request);

        var response = ResponseEntity.status(result.status())
                .cacheControl(CacheControl.noStore());

        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }

        return response.body(result.body());
    }
}
