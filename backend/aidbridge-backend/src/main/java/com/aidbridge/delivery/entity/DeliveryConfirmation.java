package com.aidbridge.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "delivery_confirmations")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "confirmed_by", nullable = false, updatable = false)
    private UUID reviewerId;

    @Column(name = "decision", nullable = false, length = 20, updatable = false)
    private String decision;

    @Column(name = "notes", columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    public DeliveryConfirmation(
            UUID deliveryId,
            UUID reviewerId,
            String decision,
            String reason,
            Instant reviewedAt
    ) {
        if (!"CONFIRMED".equals(decision)
                && !"REQUIRES_REVIEW".equals(decision)) {
            throw new IllegalArgumentException(
                    "Unsupported confirmation decision."
            );
        }

        String normalizedReason = reason == null ? null : reason.strip();
        if (normalizedReason != null && normalizedReason.isEmpty()) {
            normalizedReason = null;
        }

        if ("REQUIRES_REVIEW".equals(decision)
                && normalizedReason == null) {
            throw new IllegalArgumentException(
                    "Reason is required when review is required."
            );
        }

        this.deliveryId = Objects.requireNonNull(deliveryId);
        this.reviewerId = Objects.requireNonNull(reviewerId);
        this.decision = decision;
        this.reason = normalizedReason;
        this.reviewedAt = Objects.requireNonNull(reviewedAt);
    }
}