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

    public Delivery(
            UUID caseId,
            UUID campaignId,
            UUID recorderId,
            Outcome outcome,
            String reason,
            Instant handedOverAt,
            Instant recordedAt
    ) {
        this.caseId = caseId;
        this.campaignId = campaignId;
        this.recorderId = recorderId;
        this.outcome = outcome;
        this.reason = reason;
        this.handedOverAt = handedOverAt;
        this.recordedAt = recordedAt;
    }
}