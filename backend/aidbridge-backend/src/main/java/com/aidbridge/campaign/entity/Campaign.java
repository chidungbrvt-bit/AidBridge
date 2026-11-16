package com.aidbridge.campaign.entity;

import com.aidbridge.casefile.entity.AidCase;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aid_case_id", nullable = false, unique = true, updatable = false)
    private AidCase aidCase;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @NotBlank
    @Size(max = 160)
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @NotBlank
    @Size(max = 2000)
    @Column(name = "public_summary", nullable = false, length = 2000)
    private String publicSummary;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Campaign(
            Organization organization,
            AidCase aidCase,
            User createdBy,
            String title,
            String publicSummary
    ) {
        this.organization = Objects.requireNonNull(organization);
        this.aidCase = Objects.requireNonNull(aidCase);
        this.createdBy = Objects.requireNonNull(createdBy);
        if (!organization.getId().equals(aidCase.getOrganization().getId())) {
            throw new IllegalArgumentException("Campaign and case must belong to the same organization");
        }
        this.title = Objects.requireNonNull(title).strip();
        this.publicSummary = Objects.requireNonNull(publicSummary).strip();
        this.description = this.publicSummary;
        this.status = Status.DRAFT;
    }

    public void publish(Instant now) {
        this.status = Status.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(now);
    }

    public void markFulfilled() {
        if (status == Status.FULFILLED) {
            return;
        }

        if (status != Status.PUBLISHED) {
            throw new IllegalStateException(
                    "Only a published campaign can become fulfilled."
            );
        }

        this.status = Status.FULFILLED;
    }

    public void reopenForDispute() {
        if (status != Status.CLOSED) {
            throw new IllegalStateException(
                    "Only a closed campaign can be reopened."
            );
        }

        this.status = Status.FULFILLED;
        this.closedAt = null;
    }

    public void markPublishedAfterVoid() {
        if (status != Status.FULFILLED) {
            throw new IllegalStateException(
                    "Only a fulfilled campaign can return to published after a void."
            );
        }

        this.status = Status.PUBLISHED;
    }

    public void close(Instant now) {
        if (status != Status.FULFILLED) {
            throw new IllegalStateException("Only a fulfilled campaign can be closed.");
        }

        this.closedAt = Objects.requireNonNull(now, "now is required");
        this.status = Status.CLOSED;
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        FULFILLED,
        CLOSED
    }
}
