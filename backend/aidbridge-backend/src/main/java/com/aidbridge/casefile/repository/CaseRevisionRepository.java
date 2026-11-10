package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.CaseRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseRevisionRepository extends JpaRepository<CaseRevision, UUID> {
    @Query("""
            SELECT r.id FROM CaseRevision r
            WHERE r.caseId = :caseId AND r.organizationId = :orgId
              AND r.createdAt > (
                SELECT MAX(v.verifiedAt) FROM Verification v
                WHERE v.aidCase.id = :caseId AND v.aidCase.organization.id = :orgId
                  AND v.decision = 'CHANGES_REQUESTED'
              )
            """)
    List<UUID> findChangedAfterReview(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId, Limit limit);

    boolean existsByCaseIdAndOrganizationIdAndCaseVersion(
            UUID caseId, UUID organizationId, long caseVersion
    );
}
