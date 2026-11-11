package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.dto.request.ConsentInput;
import com.aidbridge.casefile.dto.response.CaseDetailResponse.Consent;
import com.aidbridge.casefile.entity.ConsentRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<ConsentRecord, UUID> {
    Optional<ConsentRecord> findByIdAndOrganizationIdAndBeneficiaryIdAndStatus(
            UUID id, UUID organizationId, UUID beneficiaryId, String status);

    default UUID append(UUID beneficiaryId, UUID orgId, UUID actorId, ConsentInput input) {
        var record = new ConsentRecord(beneficiaryId, orgId, actorId, input.scope(),
                input.basis().name(), input.capturedAt(), input.publicSummaryAllowed());
        return saveAndFlush(record).getId(); // AidCase references the consent by its scalar foreign key.
    }

    default Consent find(UUID id, UUID orgId, UUID beneficiaryId) {
        if (id == null) return null;
        return findByIdAndOrganizationIdAndBeneficiaryIdAndStatus(id, orgId, beneficiaryId, "GRANTED")
                .map(record -> new Consent(record.getId(), record.getBasis(), record.getScope(),
                        record.getCapturedAt(), Boolean.TRUE.equals(record.getPublicSummaryAllowed())))
                .orElse(null);
    }
}
