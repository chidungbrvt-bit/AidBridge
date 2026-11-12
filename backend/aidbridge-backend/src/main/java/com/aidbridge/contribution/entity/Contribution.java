package com.aidbridge.contribution.entity;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.shared.util.ChainRefs;
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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "contributions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contribution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false, updatable = false)
    private Campaign campaign;

    // ID only: avoids loading User outside a transaction and keeps donor PII out of every mapper.
    @NotNull
    @Column(name = "donor_user_id", nullable = false, updatable = false)
    private UUID donorUserId;

    // Future commitmentRef on chain; random, never derived from internal IDs.
    @NotNull
    @Column(name = "public_ref", nullable = false, unique = true, updatable = false, columnDefinition = "bytea")
    private byte[] publicRef;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.PLEDGED;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // Set in Java at microsecond precision, which is what PostgreSQL stores. The driver rounds
    // sub-microsecond digits, so a finer value would make G01 and G02 report different instants.
    @Column(name = "pledged_at", nullable = false, updatable = false)
    private Instant pledgedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Contribution(Campaign campaign, UUID donorUserId) {
        this.campaign = Objects.requireNonNull(campaign);
        this.organizationId = Objects.requireNonNull(campaign.getOrganization().getId());
        this.donorUserId = Objects.requireNonNull(donorUserId);
        this.publicRef = ChainRefs.randomBytes32();
        this.status = Status.PLEDGED;
        this.pledgedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Status projection after a reduction (BUSINESS_WORKFLOWS contribution states). Nothing outstanding:
     * CANCELLED if nothing was ever received, RECEIVED otherwise. Otherwise the status is unchanged.
     * Caller holds the contribution row lock.
     */
    public void settleAfterCancellation(long outstandingQuantity, long receivedQuantity) {
        if (outstandingQuantity == 0) {
            this.status = receivedQuantity == 0 ? Status.CANCELLED : Status.RECEIVED;
        }
    }

    public enum Status {
        PLEDGED,
        PARTIALLY_RECEIVED,
        RECEIVED,
        CANCELLED
    }
}