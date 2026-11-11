package com.aidbridge.casefile.mapper;
import com.aidbridge.casefile.dto.response.*;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Receives an already authorized aggregate; it never decides access. */
@Component
@RequiredArgsConstructor
public class CaseMapper {
    private final CaseNeedItemRepository needs;
    private final ConsentRepository consents;
    private final EvidenceRepository evidence;
    public CaseDetailResponse detail(AidCase c) {
        var org = c.getOrganization().getId();
        return new CaseDetailResponse(c.getId(), org,
                new CaseDetailResponse.Creator(c.getCreatedBy().getId(), c.getCreatedBy().getFullName()),
                c.getBeneficiary().getDisplayLabel(), c.getInternalTitle(), c.getPublicTitle(), c.getPublicSummary(),
                c.getApproximateArea(), c.getPriority(), c.getStatus(), c.getVersion(),
                needs.findByAidCase_IdAndAidCase_Organization_IdAndRetiredAtVersionIsNullOrderByCreatedAtAscIdAsc(c.getId(),org)
                        .stream().map(n -> new CaseDetailResponse.Need(n.getId(), n.getItemName(), n.getUnit(), n.getRequestedQuantity())).toList(),
                consents.find(c.getConsentRecordId(), org, c.getBeneficiary().getId()),
                evidence.current(c.getId(), org), c.getCreatedAt(), c.getUpdatedAt(), c.getSubmittedAt(), c.getSupersedesCaseId());
    }
    public CaseSummaryResponse summary(AidCase c) {
        return new CaseSummaryResponse(c.getId(), c.getOrganization().getId(), c.getInternalTitle(),
                c.getStatus(), c.getPriority(), c.getVersion(), c.getCreatedAt());
    }
}
