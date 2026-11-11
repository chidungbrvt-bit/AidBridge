package com.aidbridge.casefile.entity;

import com.aidbridge.organization.entity.Organization;
import com.aidbridge.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "beneficiaries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_beneficiary_reference",
                        columnNames = {"organization_id", "reference_code"}
                ),
                @UniqueConstraint(
                        name = "uq_beneficiary_org_id",
                        columnNames = {"id", "organization_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotBlank
    @Size(max = 50)
    @Column(name = "reference_code", nullable = false, length = 50)
    private String referenceCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", nullable = false, length = 20)
    private BeneficiaryType beneficiaryType = BeneficiaryType.HOUSEHOLD;

    @NotBlank
    @Size(max = 150)
    @Column(name = "display_label", nullable = false, length = 150)
    private String displayLabel;

    @Column(name = "private_data_encrypted", columnDefinition = "text")
    private String privateDataEncrypted;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Beneficiary(
            Organization organization,
            String referenceCode,
            BeneficiaryType beneficiaryType,
            String displayLabel,
            User createdBy
    ) {
        this.organization = java.util.Objects.requireNonNull(organization);
        this.referenceCode = referenceCode == null ? null : referenceCode.strip();
        this.beneficiaryType = java.util.Objects.requireNonNull(beneficiaryType);
        this.displayLabel = displayLabel == null ? null : displayLabel.strip();
        this.createdBy = java.util.Objects.requireNonNull(createdBy);
    }

    public enum BeneficiaryType {
        HOUSEHOLD,
        INDIVIDUAL
    }
}
