package com.aidbridge.delivery.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.entity.AidCase;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.delivery.dto.request.DeliveryRequest;
import com.aidbridge.delivery.dto.response.DeliveryResponse;
import com.aidbridge.delivery.entity.Delivery;
import com.aidbridge.delivery.repository.DeliveryRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class DeliveryService {
    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final DeliveryRepository deliveries;
    private final AuditEventRepository auditEvents;
    private final IdempotencyService idempotency;
    private final ObjectMapper json;

    @Transactional
    public Result<DeliveryResponse> record(
            Authentication authentication, UUID caseId, String key, DeliveryRequest request
    ) {
        var memberships = organizations.currentMemberships(authentication).stream()
                .filter(membership -> !membership.getRoles().isEmpty())
                .toList();
        var orgIds = memberships.stream()
                .map(membership -> membership.getOrganization().getId())
                .toList();
        if (orgIds.isEmpty()) {
            throw BusinessException.missing();
        }

        AidCase aidCase = cases.lockScoped(caseId, orgIds)
                .orElseThrow(BusinessException::missing);
        UUID orgId = aidCase.getOrganization().getId();
        var membership = memberships.stream()
                .filter(candidate -> orgId.equals(candidate.getOrganization().getId()))
                .findFirst()
                .orElseThrow(BusinessException::missing);
        if (!membership.getRoles().contains(OrganizationMembership.Role.VOLUNTEER)) {
            throw new AccessDeniedException("Access is denied");
        }

        UUID recorderId = membership.getUser().getId();
        return idempotency.execute(
                recorderId, orgId, "POST:/api/cases/" + caseId + "/deliveries",
                key, request, DeliveryResponse.class,
                () -> recordNew(aidCase, orgId, recorderId, request)
        );
    }

    private Result<DeliveryResponse> recordNew(
            AidCase aidCase, UUID orgId, UUID recorderId, DeliveryRequest request
    ) {
        UUID caseId = aidCase.getId();
        Campaign campaign = campaigns.lockByCaseAndOrganization(caseId, orgId)
                .orElseThrow(BusinessException::state);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) {
            throw BusinessException.missing();
        }
        if (campaigns.hasActiveDispute(caseId)) {
            throw BusinessException.disputeOpen();
        }
        if (aidCase.getStatus() != AidCase.Status.IN_PROGRESS
                || campaign.getStatus() != Campaign.Status.PUBLISHED) {
            throw BusinessException.state();
        }
        if (!"FAILED".equals(request.outcome())) {
            throw new BusinessException(
                    HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED",
                    "Successful delivery recording is not available yet"
            );
        }

        Instant recordedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Delivery delivery = deliveries.save(new Delivery(
                caseId, campaign.getId(), recorderId, Delivery.Outcome.FAILED,
                request.reason(), null, recordedAt
        ));
        UUID deliveryId = delivery.getId();

        String privatePayload = json.writeValueAsString(Map.of(
                "payloadVersion", 1,
                "deliveryId", deliveryId,
                "outcome", delivery.getOutcome().name(),
                "reason", delivery.getReason()
        ));
        AuditEvent event = auditEvents.save(new AuditEvent(
                orgId, caseId, recorderId, "DELIVERY", deliveryId,
                "DELIVERY_ATTEMPT_FAILED", deliveryId, privatePayload
        ));

        var response = new DeliveryResponse(
                deliveryId, caseId, recorderId, delivery.getOutcome().name(),
                "NOT_APPLICABLE", List.of(), List.of(), recordedAt,
                event.getId(), "NOT_APPLICABLE", null
        );
        return new Result<>(response, 201, "/api/deliveries/" + deliveryId, false);
    }
}