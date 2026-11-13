package com.aidbridge.contribution.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "goods_receipt_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceiptItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "goods_receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "contribution_id", nullable = false)
    private UUID contributionId;

    @Column(name = "contribution_item_id", nullable = false)
    private UUID contributionItemId;

    @Column(name = "received_quantity", nullable = false)
    private int receivedQuantity;

    public GoodsReceiptItem(GoodsReceipt receipt, UUID contributionItemId, int receivedQuantity) {
        this.receiptId = receipt.getId();
        this.contributionId = receipt.getContributionId();
        this.contributionItemId = contributionItemId;
        this.receivedQuantity = receivedQuantity;
    }
}
