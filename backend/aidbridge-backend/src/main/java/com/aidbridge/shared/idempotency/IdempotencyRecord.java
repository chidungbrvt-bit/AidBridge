package com.aidbridge.shared.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(length = 120, nullable = false)
    private String operation;

    @Column(name = "key", length = 128, nullable = false)
    private String requestKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_digest", length = 64, nullable = false)
    private String requestDigest;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_private", columnDefinition = "jsonb", nullable = false)
    private String privateResponse;

    @Column(name = "response_location", length = 200)
    private String responseLocation;

    public IdempotencyRecord(UUID actorId, UUID organizationId, String operation, String requestKey,
            String requestDigest, int responseStatus, String privateResponse, String responseLocation) {
        this.actorId = actorId;
        this.organizationId = organizationId;
        this.operation = operation;
        this.requestKey = requestKey;
        this.requestDigest = requestDigest;
        this.responseStatus = responseStatus;
        this.privateResponse = privateResponse;
        this.responseLocation = responseLocation;
    }
}
