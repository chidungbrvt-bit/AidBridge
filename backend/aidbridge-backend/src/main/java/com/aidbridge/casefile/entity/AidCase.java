package com.aidbridge.casefile.entity;

import com.aidbridge.organization.entity.Organization;
import com.aidbridge.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "aid_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AidCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(
            name = "case_code",
            nullable = false,
            unique = true,
            length = 50,
            updatable = false
    )
    private String caseCode;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            updatable = false
    )
    private Organization organization;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "beneficiary_id",
            nullable = false,
            updatable = false
    )
    private Beneficiary beneficiary;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by",
            nullable = false,
            updatable = false
    )
    private User createdBy;

    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "public_description", nullable = false, columnDefinition = "text")
    private String publicDescription;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority = Priority.NORMAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.DRAFT;

    @Size(max = 150)
    @Column(name = "region_label", length = 150)
    private String regionLabel;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
    @Column(name = "evidence_revision", nullable = false)
    private long evidenceRevision;
    @Column(name = "internal_title", nullable = false, length = 200)
    private String internalTitle;
    @Column(name = "public_title", length = 160)
    private String publicTitle;
    @Column(name = "public_summary", length = 2000)
    private String publicSummary;
    @Column(name = "approximate_area", length = 160)
    private String approximateArea;
    @Column(name = "consent_record_id")
    private UUID consentRecordId;
    @Column(name = "supersedes_case_id", updatable = false)
    private UUID supersedesCaseId;

    @Column(name = "public_ref", nullable = false, unique = true, updatable = false, columnDefinition = "bytea")
    private byte[] publicRef = com.aidbridge.shared.util.ChainRefs.randomBytes32();

    public AidCase(
            Organization organization,
            Beneficiary beneficiary,
            User createdBy,
            String caseCode,
            String title,
            String publicDescription,
            Priority priority,
            String regionLabel
    ) {
        this.organization = Objects.requireNonNull(organization);
        this.beneficiary = Objects.requireNonNull(beneficiary);
        this.createdBy = Objects.requireNonNull(createdBy);

        UUID organizationId = Objects.requireNonNull(
                organization.getId(),
                "Organization must be persisted"
        );
        if (!organizationId.equals(beneficiary.getOrganization().getId())) {
            throw new IllegalArgumentException(
                    "Beneficiary must belong to the same organization"
            );
        }
        this.caseCode = caseCode == null ? null : caseCode.strip();
        this.title = title == null ? null : title.strip();
        this.internalTitle = this.title;
        this.publicDescription = publicDescription == null
                ? null
                : publicDescription.strip();
        this.priority = Objects.requireNonNull(priority);
        this.regionLabel = regionLabel == null ? null : regionLabel.strip();
    }
    public void submitInitial(UUID consentId, String area, String publicTitle,
                              String publicSummary, UUID supersedesId) {
        this.consentRecordId = Objects.requireNonNull(consentId);
        this.approximateArea = area;
        this.publicTitle = publicTitle;
        this.publicSummary = publicSummary;
        this.supersedesCaseId = supersedesId;
        this.status = Status.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void edit(String internalTitle, String publicTitle, String publicSummary,
                     String area, Priority priority) {
        this.internalTitle = internalTitle;
        this.publicTitle = publicTitle;
        this.publicSummary = publicSummary;
        this.approximateArea = area;
        this.priority = priority;
        this.evidenceRevision++;
    }

    public void evidenceChanged() { this.evidenceRevision++; }

    public void resubmit() {
        this.status = Status.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public enum Priority {
        NORMAL,
        URGENT
    }
    public enum Status {
        DRAFT,
        SUBMITTED,
        CHANGES_REQUESTED,
        VERIFIED,
        REJECTED,
        IN_PROGRESS,
        COMPLETED,
        DISPUTED,
        CANCELLED
    }
    public void markVerified() {
        this.status = Status.VERIFIED;
    }

    public void markRejected() {
        this.status = Status.REJECTED;
    }

    public void markChangesRequested() {
        this.status = Status.CHANGES_REQUESTED;
    }

    public void markInProgress() {
        this.status = Status.IN_PROGRESS;
    }
}
