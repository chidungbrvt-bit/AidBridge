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
import java.util.Objects;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "disputes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute {

    public enum Status {
        OPEN,
        UNDER_REVIEW,
        RESOLVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aid_case_id", nullable = false, updatable = false)
    private UUID caseId;

    @Column(name = "delivery_id", updatable = false)
    private UUID deliveryId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(
            name = "reason",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String reason;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Dispute openForDelivery(
            UUID caseId,
            UUID deliveryId,
            UUID openedBy,
            String reason,
            Instant openedAt
    ) {
        Objects.requireNonNull(deliveryId, "deliveryId is required");
        return open(caseId, deliveryId, openedBy, reason, openedAt);
    }

    public static Dispute openForCase(
            UUID caseId,
            UUID openedBy,
            String reason,
            Instant openedAt
    ) {
        return open(caseId, null, openedBy, reason, openedAt);
    }

    private static Dispute open(
            UUID caseId,
            UUID deliveryId,
            UUID openedBy,
            String reason,
            Instant openedAt
    ) {
        Objects.requireNonNull(caseId, "caseId is required");
        Objects.requireNonNull(openedBy, "openedBy is required");
        Objects.requireNonNull(openedAt, "openedAt is required");

        String normalizedReason = reason == null ? null : reason.strip();
        if (normalizedReason == null
                || normalizedReason.isEmpty()
                || normalizedReason.length() > 2000) {
            throw new IllegalArgumentException(
                    "Reason must contain between 1 and 2000 characters."
            );
        }

        Dispute dispute = new Dispute();
        dispute.caseId = caseId;
        dispute.deliveryId = deliveryId;
        dispute.openedBy = openedBy;
        dispute.status = Status.OPEN;
        dispute.reason = normalizedReason;
        dispute.createdAt = openedAt;
        dispute.updatedAt = openedAt;
        return dispute;
    }

    public void resolve(UUID resolverId, String note, Instant now) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("Only an open dispute can be resolved.");
        }

        Objects.requireNonNull(resolverId, "resolverId is required");
        Objects.requireNonNull(now, "now is required");

        String normalizedNote = note == null ? null : note.strip();
        if (normalizedNote == null
                || normalizedNote.isEmpty()
                || normalizedNote.length() > 2000) {
            throw new IllegalArgumentException(
                    "Resolution reason must contain between 1 and 2000 characters."
            );
        }

        this.status = Status.RESOLVED;
        this.resolutionNote = normalizedNote;
        this.resolvedBy = resolverId;
        this.resolvedAt = now;
        this.updatedAt = now;
    }
}