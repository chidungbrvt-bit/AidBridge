package com.aidbridge.contribution.service;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.contribution.dto.request.ReceiptRequest;
import com.aidbridge.contribution.dto.response.ReceiptResponse;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.entity.GoodsReceipt;
import com.aidbridge.contribution.entity.GoodsReceiptItem;
import com.aidbridge.contribution.repository.ContributionItemRepository;
import com.aidbridge.contribution.repository.ContributionRepository;
import com.aidbridge.contribution.repository.GoodsReceiptItemRepository;
import com.aidbridge.contribution.repository.GoodsReceiptRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoodsReceiptService {
    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final CampaignItemRepository campaignItems;
    private final ContributionRepository contributions;
    private final ContributionItemRepository contributionItems;
    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptItemRepository receiptItems;
    private final IdempotencyService idempotency;
    private final ContributionOutboxService outbox;

    @Transactional
    public Result<ReceiptResponse> receive(Authentication auth, UUID contributionId, String key, ReceiptRequest request) {
        var memberships = organizations.currentMemberships(auth);
        var orgIds = memberships.stream().map(member -> member.getOrganization().getId()).toList();
        if (orgIds.isEmpty()) throw BusinessException.missing();

        var target = contributions.findReceiptTarget(contributionId, orgIds)
                .orElseThrow(BusinessException::missing);
        UUID orgId = target.getOrganizationId();
        var member = memberships.stream().filter(candidate -> candidate.getOrganization().getId().equals(orgId))
                .findFirst().orElseThrow(BusinessException::missing);
        if (!member.getRoles().contains(OrganizationMembership.Role.ORG_ADMIN)) {
            throw new AccessDeniedException("Access is denied");
        }
        UUID actorId = member.getUser().getId();

        // G03 and R01 lock in the same order so both consume the latest outstanding quantity.
        cases.lockScoped(target.getCaseId(), List.of(orgId)).orElseThrow(BusinessException::missing);
        Campaign campaign = campaigns.lockScoped(target.getCampaignId(), List.of(orgId))
                .orElseThrow(BusinessException::missing);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) {
            throw BusinessException.missing();
        }
        return idempotency.execute(actorId, orgId, "POST:/api/commitments/" + contributionId + "/receipts",
                key, request, ReceiptResponse.class, () -> recordReceipt(campaign, actorId, contributionId, request));
    }

    private Result<ReceiptResponse> recordReceipt(
            Campaign campaign, UUID actorId, UUID contributionId, ReceiptRequest request
    ) {
        UUID caseId = campaign.getAidCase().getId();
        if (campaign.getStatus() != Campaign.Status.PUBLISHED) throw BusinessException.state();
        if (campaigns.hasActiveDispute(caseId)) throw BusinessException.disputeOpen();

        Instant now = Instant.now();
        Instant receivedAt = request.receivedAt();
        // A legacy V3 ACTIVE campaign can have no published_at after upgrading through V10.
        Instant campaignStart = campaign.getPublishedAt() == null ? campaign.getCreatedAt() : campaign.getPublishedAt();
        if (receivedAt.isBefore(campaignStart) || receivedAt.isAfter(now.plusSeconds(300))) {
            throw BusinessException.invalid();
        }

        var refs = contributionItems.findLineRefs(contributionId);
        var lineIds = refs.stream().map(ContributionItemRepository.LineRef::getLineId).toList();
        if (lineIds.isEmpty()) throw BusinessException.state();
        campaignItems.lockByIds(campaign.getId(),
                refs.stream().map(ContributionItemRepository.LineRef::getCampaignItemId).toList());
        Contribution contribution = contributions.lockById(contributionId).orElseThrow(BusinessException::missing);
        var lines = contributionItems.lockByContribution(contributionId);
        var byId = lines.stream().collect(Collectors.toMap(line -> line.getId(), line -> line));
        if (request.items().stream().anyMatch(item -> !byId.containsKey(item.contributionItemId()))) {
            throw BusinessException.state();
        }
        if (contribution.getStatus() == Contribution.Status.CANCELLED) throw BusinessException.state();

        var received = contributionItems.receivedTotals(lineIds).stream().collect(Collectors.toMap(
                ContributionItemRepository.ItemQuantity::getItemId,
                ContributionItemRepository.ItemQuantity::getQuantity));
        var receivedLines = new ArrayList<ContributionOutboxService.ReceivedLine>();
        for (var item : request.items()) {
            var line = byId.get(item.contributionItemId());
            long previous = received.getOrDefault(line.getId(), 0L);
            if (item.quantity() > line.outstanding(previous)) throw BusinessException.quantityExceeded();
            received.put(line.getId(), previous + item.quantity());
            receivedLines.add(new ContributionOutboxService.ReceivedLine(
                    line.getId(), line.getCampaignItemId(), item.quantity()));
        }

        var receipt = receipts.saveAndFlush(new GoodsReceipt(contributionId, actorId, request.note(),
                receivedAt.truncatedTo(ChronoUnit.MICROS), now.truncatedTo(ChronoUnit.MICROS)));
        receiptItems.saveAll(receivedLines.stream().map(line ->
                new GoodsReceiptItem(receipt, line.contributionItemId(), line.quantity())).toList());
        long outstanding = lines.stream()
                .mapToLong(line -> line.outstanding(received.getOrDefault(line.getId(), 0L))).sum();
        contribution.settleAfterReceipt(outstanding);
        UUID auditEventId = outbox.enqueueReceipt(caseId, contribution, receipt, receivedLines);

        var response = new ReceiptResponse(receipt.getId(), contributionId, receivedLines.stream()
                .map(line -> new ReceiptResponse.Item(line.contributionItemId(), line.quantity())).toList(),
                receipt.getReceivedAt(), receipt.getRecordedAt(), actorId, auditEventId, "PENDING_ON_CHAIN");
        return new Result<>(response, 201, "/api/receipts/" + receipt.getId(), false);
    }
}
