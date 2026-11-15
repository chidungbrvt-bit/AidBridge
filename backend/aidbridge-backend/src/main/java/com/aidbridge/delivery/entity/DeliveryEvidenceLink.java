package com.aidbridge.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Immutable snapshot of one evidence version reviewed at handover. The V15 composite FK
 * (evidence_version_id, aid_case_id, organization_id) rejects evidence from another case or organization.
 * created_at is left to the database default.
 */
@Entity
@Immutable
@Getter
@Table(name = "delivery_evidence_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryEvidenceLink {
    static final String HANDOVER_PROOF = "HANDOVER_PROOF";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "evidence_version_id", nullable = false)
    private UUID evidenceVersionId;

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "linked_by", nullable = false)
    private UUID linkedBy;

    @Column(name = "aid_case_id", nullable = false)
    private UUID caseId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    public DeliveryEvidenceLink(Delivery delivery, UUID evidenceVersionId, UUID linkedBy, UUID organizationId) {
        this.deliveryId = Objects.requireNonNull(delivery.getId());
        this.caseId = Objects.requireNonNull(delivery.getCaseId());
        this.evidenceVersionId = Objects.requireNonNull(evidenceVersionId);
        this.linkedBy = Objects.requireNonNull(linkedBy);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.purpose = HANDOVER_PROOF;
    }
}