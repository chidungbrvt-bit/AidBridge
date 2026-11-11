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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "consent_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;

    @Column(name = "consent_scope", length = 200, nullable = false)
    private String scope;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 20)
    private String basis;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "public_summary_allowed")
    private Boolean publicSummaryAllowed;

    public ConsentRecord(UUID beneficiaryId, UUID organizationId, UUID recordedBy, String scope,
            String basis, Instant capturedAt, boolean publicSummaryAllowed) {
        this.beneficiaryId = beneficiaryId;
        this.organizationId = organizationId;
        this.recordedBy = recordedBy;
        this.scope = scope;
        this.status = "GRANTED";
        this.basis = basis;
        this.capturedAt = capturedAt;
        this.publicSummaryAllowed = publicSummaryAllowed;
    }
}
