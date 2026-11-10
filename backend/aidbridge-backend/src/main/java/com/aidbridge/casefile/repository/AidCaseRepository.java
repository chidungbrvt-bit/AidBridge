package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.AidCase;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AidCaseRepository extends JpaRepository<AidCase, UUID> {
    @EntityGraph(attributePaths = {"organization", "beneficiary", "createdBy"})
    Optional<AidCase> findByIdAndOrganization_IdIn(UUID id, Collection<UUID> organizationIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AidCase c where c.id = :id and c.organization.id in :orgs")
    Optional<AidCase> lockScoped(@Param("id") UUID id, @Param("orgs") Collection<UUID> orgs);

    Page<AidCase> findAllByOrganization_IdAndStatus(UUID organizationId, AidCase.Status status, Pageable pageable);
    @EntityGraph(attributePaths = {
            "organization",
            "beneficiary",
            "createdBy"
    })
    Optional<AidCase> findByIdAndOrganization_Id(
            UUID caseId,
            UUID organizationId
    );

    @EntityGraph(attributePaths = {
            "organization",
            "beneficiary",
            "createdBy"
    })
    Page<AidCase> findAllByOrganization_Id(
            UUID organizationId,
            Pageable pageable
    );
}
