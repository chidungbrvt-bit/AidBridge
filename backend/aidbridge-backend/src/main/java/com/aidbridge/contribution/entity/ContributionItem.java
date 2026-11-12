package com.aidbridge.contribution.entity;

import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.shared.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contribution_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContributionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @NotNull
    @Column(name = "campaign_id", nullable = false, updatable = false)
    private UUID campaignId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contribution_id", nullable = false, updatable = false)
    private Contribution contribution;

    @NotNull
    @Column(name = "campaign_item_id", nullable = false, updatable = false)
    private UUID campaignItemId;

    // The original pledge is never rewritten; reductions only increase cancelledQuantity.
    @Min(1)
    @Max(1_000_000)
    @Column(name = "pledged_quantity", nullable = false, updatable = false)
    private int pledgedQuantity;

    @Min(0)
    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity;

    public ContributionItem(Contribution contribution, CampaignItem campaignItem, int quantity) {
        Objects.requireNonNull(contribution);
        Objects.requireNonNull(campaignItem);
        UUID contributionCampaignId = Objects.requireNonNull(contribution.getCampaign().getId());
        if (!contributionCampaignId.equals(campaignItem.getCampaign().getId())) {
            throw new IllegalArgumentException("Campaign item must belong to the contribution campaign");
        }
        this.contribution = contribution;
        this.organizationId = contribution.getOrganizationId();
        this.campaignId = contributionCampaignId;
        this.campaignItemId = Objects.requireNonNull(campaignItem.getId());
        this.pledgedQuantity = quantity;
        this.cancelledQuantity = 0;
    }

    /** pledged - cancelled - received: what the donor may still cancel and the organization may still receive. */
    public long outstanding(long receivedQuantity) {
        return (long) pledgedQuantity - cancelledQuantity - receivedQuantity;
    }

    /**
     * Cancels part of the unreceived remainder. The original pledge is never rewritten.
     * Caller holds this row's lock and passes the received total read under that lock.
     */
    public void cancel(int quantity, long receivedQuantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (quantity > outstanding(receivedQuantity)) {
            throw BusinessException.quantityExceeded();
        }
        this.cancelledQuantity += quantity;
    }
}