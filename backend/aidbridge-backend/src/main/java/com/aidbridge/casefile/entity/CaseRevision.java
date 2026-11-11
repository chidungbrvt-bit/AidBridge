package com.aidbridge.casefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "case_revisions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "aid_case_id", nullable = false)
    private UUID caseId;

    @Column(name = "case_version", nullable = false)
    private long caseVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_private", columnDefinition = "jsonb", nullable = false)
    private String privateSnapshot;

    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private Instant createdAt;

    public CaseRevision(UUID organizationId, UUID caseId, long caseVersion, String privateSnapshot) {
        this.organizationId = organizationId;
        this.caseId = caseId;
        this.caseVersion = caseVersion;
        this.privateSnapshot = privateSnapshot;
    }
}
