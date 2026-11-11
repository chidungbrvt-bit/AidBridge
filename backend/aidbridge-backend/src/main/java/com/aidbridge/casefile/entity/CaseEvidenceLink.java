package com.aidbridge.casefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "case_evidence_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseEvidenceLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aid_case_id", nullable = false)
    private UUID caseId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_version_id", nullable = false)
    private EvidenceVersion evidenceVersion;

    @Column(name = "linked_by", nullable = false)
    private UUID linkedBy;

    @Column(name = "retired_at_version")
    private Long retiredAtVersion;

    public CaseEvidenceLink(UUID caseId, UUID organizationId, EvidenceVersion evidenceVersion, UUID linkedBy) {
        this.caseId = caseId;
        this.organizationId = organizationId;
        this.evidenceVersion = evidenceVersion;
        this.linkedBy = linkedBy;
    }

    public void retireAt(long caseVersion) {
        this.retiredAtVersion = caseVersion;
    }
}
