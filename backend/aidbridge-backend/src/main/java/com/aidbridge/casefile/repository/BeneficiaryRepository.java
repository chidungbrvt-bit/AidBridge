package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.Beneficiary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    @EntityGraph(attributePaths = {"organization", "createdBy"})
    Optional<Beneficiary> findByIdAndOrganization_Id(
            UUID beneficiaryId,
            UUID organizationId
    );
    boolean existsByOrganization_IdAndReferenceCode(
            UUID organizationId,
            String referenceCode
    );
}