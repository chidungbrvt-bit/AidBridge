package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.Dispute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    @Query("""
            select dispute
            from Dispute dispute
            where dispute.id = :disputeId
              and exists (
                  select aidCase.id
                  from AidCase aidCase
                  where aidCase.id = dispute.caseId
                    and aidCase.organization.id = :organizationId
              )
            """)
    Optional<Dispute> findScoped(
            @Param("organizationId") UUID organizationId,
            @Param("disputeId") UUID disputeId
    );

    @Query("""
            select count(dispute)
            from Dispute dispute
            where dispute.caseId = :caseId
              and dispute.status in :statuses
              and exists (
                  select aidCase.id
                  from AidCase aidCase
                  where aidCase.id = dispute.caseId
                    and aidCase.organization.id = :organizationId
              )
            """)
    long countByCaseAndStatusesScoped(
            @Param("organizationId") UUID organizationId,
            @Param("caseId") UUID caseId,
            @Param("statuses") Collection<Dispute.Status> statuses
    );

    @Query("""
        select dispute
        from Dispute dispute
        where dispute.caseId = :caseId
          and exists (
              select aidCase.id
              from AidCase aidCase
              where aidCase.id = dispute.caseId
                and aidCase.organization.id = :organizationId
          )
        order by dispute.createdAt desc, dispute.id desc
        """)
    List<Dispute> findAllForCaseScoped(
            @Param("caseId") UUID caseId,
            @Param("organizationId") UUID organizationId
    );

    interface DisputeRef {
        UUID getCaseId();
        UUID getOrganizationId();
    }

    @Query("""
        select aidCase.id as caseId,
               aidCase.organization.id as organizationId
        from Dispute dispute, AidCase aidCase
        where dispute.id = :disputeId
          and aidCase.id = dispute.caseId
        """)
    Optional<DisputeRef> findRefForResolution(@Param("disputeId") UUID disputeId);
}