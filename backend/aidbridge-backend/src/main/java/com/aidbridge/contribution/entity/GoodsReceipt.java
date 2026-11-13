package com.aidbridge.contribution.entity;

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
@Getter
@Table(name = "goods_receipts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GoodsReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contribution_id", nullable = false)
    private UUID contributionId;

    @Column(name = "received_by", nullable = false)
    private UUID recorderId;

    @Column(name = "receipt_note", columnDefinition = "text")
    private String note;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant recordedAt;

    public GoodsReceipt(UUID contributionId, UUID recorderId, String note, Instant receivedAt, Instant recordedAt) {
        this.contributionId = contributionId;
        this.recorderId = recorderId;
        this.note = note;
        this.receivedAt = receivedAt;
        this.recordedAt = recordedAt;
    }
}
