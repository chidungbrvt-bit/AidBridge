package com.aidbridge.casefile.controller;

import com.aidbridge.casefile.dto.response.StaffCaseDetailResponse;
import com.aidbridge.casefile.service.StaffCaseService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StaffCaseController {
    private final StaffCaseService service;

    @GetMapping("/api/org/cases/{id}")
    public ResponseEntity<StaffCaseDetailResponse> get(Authentication actor, @PathVariable("id") UUID caseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor, caseId));
    }
}
