package com.aidbridge.verification.repository;

import com.aidbridge.verification.entity.Verification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {
    @Query("""
            SELECT v FROM Verification v
            WHERE v.id = :id AND v.aidCase.organization.id = :orgId
              AND v.decision = 'APPROVED' AND v.reviewedCaseVersion IS NOT NULL
            """)
    Optional<Verification> findApproved(@Param("orgId") UUID organizationId, @Param("id") UUID verificationId);

    @Query("""
            SELECT v FROM Verification v
            WHERE v.aidCase.id = :caseId AND v.aidCase.organization.id = :orgId
            ORDER BY v.verifiedAt DESC, v.id DESC
            """)
    List<Verification> findLatestByCase(
            @Param("caseId") UUID caseId,
            @Param("orgId") UUID organizationId,
            Limit limit
    );
}
