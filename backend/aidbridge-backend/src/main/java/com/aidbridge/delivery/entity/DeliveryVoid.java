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
@Table(name = "delivery_voids")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryVoid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "aid_case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;

    @Column(name = "voided_by", nullable = false, updatable = false)
    private UUID voidedBy;

    @Column(
            name = "reason",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String reason;

    @Column(name = "voided_at", nullable = false, updatable = false)
    private Instant voidedAt;

    public DeliveryVoid(
            UUID deliveryId,
            UUID caseId,
            UUID disputeId,
            UUID voidedBy,
            String reason,
            Instant voidedAt
    ) {
        String normalizedReason = reason == null ? null : reason.strip();

        if (normalizedReason == null
                || normalizedReason.isEmpty()
                || normalizedReason.length() > 2000) {
            throw new IllegalArgumentException(
                    "Reason must contain between 1 and 2000 characters."
            );
        }

        this.deliveryId = Objects.requireNonNull(deliveryId);
        this.caseId = Objects.requireNonNull(caseId);
        this.disputeId = Objects.requireNonNull(disputeId);
        this.voidedBy = Objects.requireNonNull(voidedBy);
        this.reason = normalizedReason;
        this.voidedAt = Objects.requireNonNull(voidedAt);
    }
}