package com.aidbridge.casefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "evidence_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceFile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "aid_case_id")
    private UUID caseId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(name = "access_level", length = 20, nullable = false)
    private String accessLevel;

    public EvidenceFile(UUID caseId, UUID organizationId, UUID uploadedBy, String title) {
        this.caseId = caseId;
        this.organizationId = organizationId;
        this.uploadedBy = uploadedBy;
        this.title = title;
        this.accessLevel = "PRIVATE";
    }
}
