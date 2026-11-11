package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.dto.response.EvidenceVersionResponse;
import com.aidbridge.casefile.entity.EvidenceVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository extends JpaRepository<EvidenceVersion, UUID> {
    record StoredVersion(UUID caseId, UUID orgId, String fixtureKey, String storageKey,
                         EvidenceVersionResponse metadata) { }

    @Query("""
            SELECT v FROM EvidenceVersion v JOIN v.file f
            WHERE v.id = :id AND v.organizationId IN :orgIds AND f.caseId = v.caseId
            """)
    Optional<EvidenceVersion> findScoped(@Param("id") UUID id, @Param("orgIds") Collection<UUID> orgIds);

    @Query("""
            SELECT v FROM CaseEvidenceLink link JOIN link.evidenceVersion v
            WHERE link.caseId = :caseId AND link.organizationId = :orgId
              AND link.retiredAtVersion IS NULL
            ORDER BY v.createdAt, v.id
            """)
    List<EvidenceVersion> findCurrent(@Param("caseId") UUID caseId, @Param("orgId") UUID orgId);

    default Optional<StoredVersion> scoped(UUID id, Collection<UUID> orgIds) {
        if (orgIds.isEmpty()) return Optional.empty();
        return findScoped(id, orgIds).map(version -> new StoredVersion(version.getCaseId(),
                version.getOrganizationId(), version.getFixtureKey(), version.getStorageKey(), metadata(version)));
    }

    default List<EvidenceVersionResponse> current(UUID caseId, UUID orgId) {
        return findCurrent(caseId, orgId).stream().map(EvidenceRepository::metadata).toList();
    }

    static EvidenceVersionResponse metadata(EvidenceVersion version) {
        return new EvidenceVersionResponse(version.getId(), version.getFile().getId(), version.getVersionNo(),
                version.getMimeType(), version.getByteSize(), version.getSha256().strip(), version.getCreatedAt());
    }
}
