package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.CaseEvidenceLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseEvidenceLinkRepository extends JpaRepository<CaseEvidenceLink, UUID> {
    @Query("""
            SELECT link FROM CaseEvidenceLink link
            WHERE link.caseId = :caseId AND link.organizationId = :orgId
              AND link.evidenceVersion.id = :versionId AND link.retiredAtVersion IS NULL
            """)
    List<CaseEvidenceLink> findCurrent(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId,
                                      @Param("versionId") UUID versionId);
}
