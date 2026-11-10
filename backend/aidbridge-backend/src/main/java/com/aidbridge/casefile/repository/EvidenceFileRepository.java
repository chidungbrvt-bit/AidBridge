package com.aidbridge.casefile.repository;

import com.aidbridge.casefile.entity.EvidenceFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, UUID> {
}
