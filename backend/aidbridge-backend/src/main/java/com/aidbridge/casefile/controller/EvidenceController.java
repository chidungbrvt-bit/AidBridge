package com.aidbridge.casefile.controller;

import com.aidbridge.casefile.dto.response.EvidenceFixtureResponse;
import com.aidbridge.casefile.service.CaseService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class EvidenceController {
    private final CaseService service;
    @GetMapping("/api/evidence-fixtures")
    public ResponseEntity<List<EvidenceFixtureResponse>> fixtures(Authentication auth, @RequestParam UUID organizationId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.fixtureList(auth,organizationId));
    }
    @GetMapping("/api/evidence-versions/{id}/content")
    public ResponseEntity<byte[]> content(Authentication auth, @PathVariable UUID id) {
        var content = service.content(auth,id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(content.filename()).build().toString())
                .header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
