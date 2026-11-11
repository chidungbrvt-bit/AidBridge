package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.CaseNeedItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseNeedItemRepository extends JpaRepository<CaseNeedItem, UUID> {
    List<CaseNeedItem>
    findByAidCase_IdAndAidCase_Organization_IdAndRetiredAtVersionIsNullOrderByCreatedAtAscIdAsc(
            UUID caseId,
            UUID organizationId
    );
}
