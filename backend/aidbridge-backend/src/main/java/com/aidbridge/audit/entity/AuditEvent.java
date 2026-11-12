package com.aidbridge.audit.entity;

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
@Table(name = "audit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Getter
    @Column(name = "aid_case_id")
    private UUID caseId;

    @Column(name = "actor_user_id")
    private UUID actorId;

    @Column(name = "entity_type", length = 60, nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "event_key", nullable = false)
    private UUID eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_private", columnDefinition = "jsonb", nullable = false)
    private String privatePayload;

    public AuditEvent(UUID organizationId, UUID caseId, UUID actorId, String entityType,
            UUID entityId, String eventType, UUID eventKey, String privatePayload) {
        this.organizationId = organizationId;
        this.caseId = caseId;
        this.actorId = actorId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.privatePayload = privatePayload;
    }
}
