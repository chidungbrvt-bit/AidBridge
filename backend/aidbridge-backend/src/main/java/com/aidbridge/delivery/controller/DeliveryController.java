package com.aidbridge.delivery.controller;

import com.aidbridge.delivery.dto.request.ConfirmationRequest;
import com.aidbridge.delivery.dto.request.DeliveryRequest;
import com.aidbridge.delivery.dto.response.ConfirmationResponse;
import com.aidbridge.delivery.dto.response.DeliveryResponse;
import com.aidbridge.delivery.service.ConfirmationService;
import com.aidbridge.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService deliveries;
    private final ConfirmationService confirmations;

    @PostMapping("/api/cases/{id}/deliveries")
    public ResponseEntity<DeliveryResponse> record(
            Authentication authentication,
            @PathVariable("id") UUID caseId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeliveryRequest request
    ) {
        var result = deliveries.record(authentication, caseId, key, request);
        var response = ResponseEntity.status(result.status())
                .location(URI.create(result.location()))
                .cacheControl(CacheControl.noStore());

        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.body());
    }

    @PostMapping("/api/deliveries/{id}/confirm")
    public ResponseEntity<ConfirmationResponse> confirm(
            Authentication authentication,
            @PathVariable("id") UUID deliveryId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String key,
            @Valid @RequestBody ConfirmationRequest request
    ) {
        var result = confirmations.confirm(
                authentication,
                deliveryId,
                key,
                request
        );

        var response = ResponseEntity.status(result.status())
                .cacheControl(CacheControl.noStore());

        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }

        return response.body(result.body());
    }
}