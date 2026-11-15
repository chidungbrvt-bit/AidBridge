package com.aidbridge.campaign.controller;

import com.aidbridge.campaign.dto.request.CreateCampaignRequest;
import com.aidbridge.campaign.dto.response.CampaignPage;
import com.aidbridge.campaign.dto.response.CampaignResponse;
import com.aidbridge.campaign.service.CampaignService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService service;

    @PostMapping
    public ResponseEntity<CampaignResponse> create(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        return toResponse(service.create(authentication, idempotencyKey, request));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<CampaignResponse> publish(
            Authentication authentication,
            @PathVariable("id") UUID campaignId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (body != null && !body.isEmpty()) {
            throw BusinessException.invalid();
        }
        return toResponse(service.publish(authentication, campaignId, idempotencyKey));
    }

    @GetMapping
    public ResponseEntity<CampaignPage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listPublic(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> get(
            Authentication authentication,
            @PathVariable("id") UUID campaignId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getById(authentication, campaignId));
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

    @PostMapping("/{id}/close")
    public ResponseEntity<CampaignResponse> close(
            Authentication authentication,
            @PathVariable("id") UUID campaignId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (body != null && !body.isEmpty()) {
            throw BusinessException.invalid();
        }

        return toResponse(service.close(authentication, campaignId, key));
    }
}
