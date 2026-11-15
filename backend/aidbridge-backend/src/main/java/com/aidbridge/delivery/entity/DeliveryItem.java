package com.aidbridge.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One delivered line. Immutable (V15 trigger); the campaign is copied from the header for the composite FK. */
@Entity
@Immutable
@Getter
@Table(name = "delivery_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "campaign_item_id", nullable = false)
    private UUID campaignItemId;

    @Column(name = "delivered_quantity", nullable = false)
    private int quantity;

    public DeliveryItem(Delivery delivery, UUID campaignItemId, int quantity) {
        this.deliveryId = Objects.requireNonNull(delivery.getId());
        this.campaignId = Objects.requireNonNull(delivery.getCampaignId());
        this.campaignItemId = Objects.requireNonNull(campaignItemId);
        this.quantity = quantity;
    }
}