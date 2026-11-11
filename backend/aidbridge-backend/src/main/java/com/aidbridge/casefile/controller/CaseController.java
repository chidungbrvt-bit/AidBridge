package com.aidbridge.casefile.controller;

import com.aidbridge.casefile.dto.request.*;
import com.aidbridge.casefile.dto.response.*;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.service.CaseService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {
    private final CaseService service;
    @PostMapping
    public ResponseEntity<CaseDetailResponse> create(Authentication auth,
            @RequestHeader(value="Idempotency-Key", required=false) String key,
            @Valid @RequestBody CreateCaseRequest request) {
        return response(service.create(auth,key,request));
    }

    @GetMapping
    public ResponseEntity<CasePage> list(Authentication auth, @RequestParam UUID organizationId,
            @RequestParam(required=false) AidCase.Status status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(auth,organizationId,status,page,size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseDetailResponse> get(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(auth,id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CaseDetailResponse> patch(Authentication auth, @PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key", required=false) String key,
            @Valid @RequestBody PatchCaseRequest request) {
        return response(service.patch(auth,id,key,request));
    }

    @PostMapping("/{id}/resubmit")
    public ResponseEntity<CaseDetailResponse> resubmit(Authentication auth, @PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key", required=false) String key,
            @Valid @RequestBody ResubmitCaseRequest request) {
        return response(service.resubmit(auth,id,key,request));
    }

    @PostMapping("/{id}/evidence-versions")
    public ResponseEntity<EvidenceVersionResponse> evidence(Authentication auth, @PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key", required=false) String key,
            @Valid @RequestBody EvidenceVersionRequest request) {
        return response(service.appendEvidence(auth,id,key,request));
    }

    static <T> ResponseEntity<T> response(Result<T> result) {
        var response = ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore());
        if (result.location()!=null) response.location(URI.create(result.location()));
        if (result.replayed()) response.header("Idempotent-Replayed","true");
        return response.body(result.body());
    }
}
