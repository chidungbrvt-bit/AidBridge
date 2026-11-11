package com.aidbridge.campaign.entity;

import com.aidbridge.casefile.entity.CaseNeedItem;
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

@Entity
@Table(name = "campaign_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignItem {
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

    @NotNull
    @Column(name = "case_need_item_id", nullable = false, updatable = false)
    private UUID caseNeedItemId;

    @NotBlank
    @Size(max = 120)
    @Column(name = "item_name", nullable = false, length = 120, updatable = false)
    private String itemName;

    @NotBlank
    @Size(max = 40)
    @Column(name = "unit", nullable = false, length = 40, updatable = false)
    private String unit;

    @Min(1)
    @Max(1_000_000)
    @Column(name = "target_quantity", nullable = false, updatable = false)
    private int targetQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CampaignItem(Campaign campaign, CaseNeedItem needItem) {
        Objects.requireNonNull(campaign);
        Objects.requireNonNull(needItem);
        this.campaign = campaign;
        this.organizationId = campaign.getOrganization().getId();
        this.caseNeedItemId = Objects.requireNonNull(needItem.getId());
        this.itemName = needItem.getItemName();
        this.unit = needItem.getUnit();
        this.targetQuantity = needItem.getRequestedQuantity();
    }
}
