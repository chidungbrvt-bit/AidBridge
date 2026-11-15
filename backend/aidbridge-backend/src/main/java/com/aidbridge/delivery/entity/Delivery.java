package com.aidbridge.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {
    public enum Outcome {
        DELIVERED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aid_case_id", nullable = false)
    private UUID caseId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "recorded_by", nullable = false)
    private UUID recorderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private Outcome outcome;

    @Column(name = "handover_note", columnDefinition = "text")
    private String reason;

    @Column(name = "delivered_at")
    private Instant handedOverAt;

    @Column(name = "created_at", nullable = false)
    private Instant recordedAt;

    /** Opaque bytes32 for the registry (D56). Generated once at creation; null for FAILED attempts. */
    @Column(name = "delivery_ref", updatable = false, columnDefinition = "bytea")
    private byte[] deliveryRef;

    public Delivery(
            UUID caseId,
            UUID campaignId,
            UUID recorderId,
            Outcome outcome,
            String reason,
            Instant handedOverAt,
            Instant recordedAt,
            byte[] deliveryRef
    ) {
        // Mirrors chk_delivery_ref_by_outcome: only DELIVERED carries a ref, and it must be a full bytes32.
        if ((outcome == Outcome.DELIVERED) != (deliveryRef != null)
                || (deliveryRef != null && deliveryRef.length != 32)) {
            throw new IllegalArgumentException("deliveryRef is required for DELIVERED only and must be 32 bytes");
        }
        this.caseId = caseId;
        this.campaignId = campaignId;
        this.recorderId = recorderId;
        this.outcome = outcome;
        this.reason = reason;
        this.handedOverAt = handedOverAt;
        this.recordedAt = recordedAt;
        this.deliveryRef = deliveryRef;
    }
}