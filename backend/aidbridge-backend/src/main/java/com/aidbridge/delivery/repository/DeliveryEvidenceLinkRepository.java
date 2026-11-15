package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryEvidenceLink;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEvidenceLinkRepository extends JpaRepository<DeliveryEvidenceLink, UUID> {
}