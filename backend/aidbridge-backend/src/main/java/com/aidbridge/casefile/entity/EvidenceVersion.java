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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evidence_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_file_id", nullable = false, updatable = false)
    private EvidenceFile file;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "aid_case_id", updatable = false)
    private UUID caseId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "storage_key", columnDefinition = "text", nullable = false, updatable = false)
    private String storageKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256_hex", length = 64, nullable = false, updatable = false)
    private String sha256;

    @Column(name = "mime_type", length = 120, nullable = false, updatable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "fixture_key", length = 120, updatable = false)
    private String fixtureKey;

    @Column(name = "supersedes_id", updatable = false)
    private UUID predecessorId;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private Instant createdAt;

    public EvidenceVersion(EvidenceFile file, UUID caseId, UUID organizationId, UUID uploadedBy,
            int versionNo, String fixtureKey, String sha256, long byteSize, UUID predecessorId) {
        this.file = file;
        this.caseId = caseId;
        this.organizationId = organizationId;
        this.uploadedBy = uploadedBy;
        this.versionNo = versionNo;
        this.fixtureKey = fixtureKey;
        this.sha256 = sha256;
        this.byteSize = byteSize;
        this.predecessorId = predecessorId;
        this.storageKey = "fixture-version:" + UUID.randomUUID();
        this.mimeType = "text/plain";
    }
}
