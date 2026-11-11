package com.aidbridge.contribution.service;

import com.aidbridge.campaign.entity.Campaign;
import com.aidbridge.campaign.entity.CampaignItem;
import com.aidbridge.campaign.repository.CampaignItemRepository;
import com.aidbridge.campaign.repository.CampaignRepository;
import com.aidbridge.casefile.repository.AidCaseRepository;
import com.aidbridge.contribution.dto.request.CancellationRequest;
import com.aidbridge.contribution.dto.request.CommitmentRequest;
import com.aidbridge.contribution.dto.response.CommitmentPage;
import com.aidbridge.contribution.dto.response.CommitmentResponse;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.entity.ContributionItem;
import com.aidbridge.contribution.mapper.ContributionMapper;
import com.aidbridge.contribution.repository.ContributionItemRepository;
import com.aidbridge.contribution.repository.ContributionRepository;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import com.aidbridge.user.entity.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContributionService {

    /** Deterministic line order for every response (Q5); never insertion order or random UUIDs. */
    private static final Comparator<CampaignItem> DISPLAY_ORDER = Comparator
            .comparing((CampaignItem i) -> i.getItemName().toLowerCase(Locale.ROOT))
            .thenComparing(i -> i.getUnit().toLowerCase(Locale.ROOT))
            .thenComparing(i -> i.getId().toString());

    // Every new pledge is queued for Fuji in the same transaction; the worker confirms it later.
    private static final String PENDING_ON_CHAIN = "PENDING_ON_CHAIN";

    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final CampaignRepository campaigns;
    private final CampaignItemRepository campaignItems;
    private final ContributionRepository contributions;
    private final ContributionItemRepository contributionItems;
    private final IdempotencyService idempotency;
    private final ContributionMapper mapper;
    private final ContributionOutboxService outbox;

    @Transactional
    public Result<CommitmentResponse> pledge(
            Authentication auth, UUID campaignId, String key, CommitmentRequest request
    ) {
        // Runs before any replay, so a revoked grant cannot read back an earlier response.
        User donor = requireDonor(auth);

        // Scalars only: the first load of every entity below is the locked load (stale-state rule F5).
        var ref = campaigns.findPublicRef(campaignId, Campaign.Status.DRAFT, Organization.Status.APPROVED)
                .orElseThrow(BusinessException::missing);
        UUID orgId = ref.getOrganizationId();

        // Canonical lock order: case -> campaign -> campaign items (by id) -> contribution rows.
        // The case lock also serializes chain_sequence assignment in the outbox.
        cases.lockScoped(ref.getCaseId(), List.of(orgId)).orElseThrow(BusinessException::missing);
        Campaign campaign = campaigns.lockScoped(campaignId, List.of(orgId)).orElseThrow(BusinessException::missing);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) {
            throw BusinessException.missing();
        }

        return idempotency.execute(donor.getId(), orgId, "POST:/api/campaigns/" + campaignId + "/commitments",
                key, request, CommitmentResponse.class, () -> {
                    if (campaign.getStatus() != Campaign.Status.PUBLISHED) throw BusinessException.state();
                    if (campaigns.hasActiveDispute(ref.getCaseId())) throw BusinessException.disputeOpen();

                    Map<UUID, Integer> requested = request.items().stream().collect(Collectors.toMap(
                            CommitmentRequest.Line::campaignItemId, CommitmentRequest.Line::quantity));
                    List<CampaignItem> locked = campaignItems.lockByIds(campaignId, requested.keySet());
                    // Unknown IDs and IDs of other campaigns are indistinguishable on purpose.
                    if (locked.size() != requested.size()) throw BusinessException.invalid();

                    // Read totals only after the item rows are locked. Under READ COMMITTED this statement
                    // takes a fresh snapshot, so it sees every pledge committed by the previous lock holder.
                    Map<UUID, Long> active = toMap(contributionItems.activeTotals(requested.keySet()));
                    for (CampaignItem item : locked) {
                        long after = active.getOrDefault(item.getId(), 0L) + requested.get(item.getId());
                        if (after > item.getTargetQuantity()) throw BusinessException.quantityExceeded();
                    }

                    Contribution contribution = contributions.saveAndFlush(new Contribution(campaign, donor.getId()));
                    List<ContributionItem> lines = contributionItems.saveAllAndFlush(locked.stream()
                            .sorted(DISPLAY_ORDER)
                            .map(item -> new ContributionItem(contribution, item, requested.get(item.getId())))
                            .toList());

                    UUID auditEventId = outbox.enqueuePledge(ref.getCaseId(), contribution, lines);
                    return new Result<>(
                            mapper.toResponse(contribution, lines, Map.of(), auditEventId, PENDING_ON_CHAIN),
                            201, "/api/commitments/" + contribution.getId(), false);
                });
    }

    /** G02: the caller's own contributions, newest first, with live chain status. Constant query count per page. */
    public CommitmentPage listMine(Authentication auth, int page, int size) {
        User donor = requireDonor(auth);
        if (page < 0 || size < 1 || size > 100) throw BusinessException.invalid();

        var result = contributions.findByDonorUserId(donor.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("pledgedAt"), Sort.Order.asc("id"))));
        List<Contribution> content = result.getContent();
        if (content.isEmpty()) {
            return new CommitmentPage(List.of(), page, size, result.getTotalElements());
        }

        List<UUID> ids = content.stream().map(Contribution::getId).toList();
        Map<UUID, List<ContributionItem>> linesByContribution = contributionItems.findDisplayOrdered(ids).stream()
                .collect(Collectors.groupingBy(line -> line.getContribution().getId()));
        Map<UUID, Long> received = receivedFor(linesByContribution.values().stream()
                .flatMap(List::stream).map(ContributionItem::getId).toList());
        Map<UUID, ContributionRepository.PledgeChainRef> chain = contributions.findPledgeChainRefs(ids).stream()
                .collect(Collectors.toMap(ContributionRepository.PledgeChainRef::getContributionId, Function.identity()));

        var items = content.stream().map(contribution -> {
            var ref = chain.get(contribution.getId());
            return mapper.toResponse(contribution,
                    linesByContribution.getOrDefault(contribution.getId(), List.of()), received,
                    ref == null ? null : ref.getAuditEventId(),
                    ContributionMapper.chainStatus(ref == null ? null : ref.getJobStatus()));
        }).toList();
        return new CommitmentPage(items, page, size, result.getTotalElements());
    }

    /** G03: cancels part of the unreceived remainder of the caller's own contribution. */
    @Transactional
    public Result<CommitmentResponse> cancel(
            Authentication auth, UUID contributionId, String key, CancellationRequest request
    ) {
        User donor = requireDonor(auth);

        // Scalars only (F5). Another donor's contribution is indistinguishable from a missing one.
        var target = contributions.findCancelTarget(contributionId)
                .filter(t -> t.getDonorUserId().equals(donor.getId()))
                .orElseThrow(BusinessException::missing);
        UUID orgId = target.getOrganizationId();
        List<ContributionItemRepository.LineRef> refs = contributionItems.findLineRefs(contributionId);

        // Canonical lock order: case -> campaign -> campaign items (by id) -> contribution header -> lines (by id).
        cases.lockScoped(target.getCaseId(), List.of(orgId)).orElseThrow(BusinessException::missing);
        Campaign campaign = campaigns.lockScoped(target.getCampaignId(), List.of(orgId))
                .orElseThrow(BusinessException::missing);
        if (campaign.getOrganization().getStatus() != Organization.Status.APPROVED) {
            throw BusinessException.missing();
        }

        return idempotency.execute(donor.getId(), orgId, "POST:/api/commitments/" + contributionId + "/cancellations",
                key, request, CommitmentResponse.class, () -> {
                    if (campaign.getStatus() != Campaign.Status.PUBLISHED) throw BusinessException.state();
                    if (campaigns.hasActiveDispute(target.getCaseId())) throw BusinessException.disputeOpen();

                    Map<UUID, Integer> requested = request.items().stream().collect(Collectors.toMap(
                            CancellationRequest.Line::contributionItemId, CancellationRequest.Line::quantity));
                    Set<UUID> ownLines = refs.stream()
                            .map(ContributionItemRepository.LineRef::getLineId).collect(Collectors.toSet());
                    if (!ownLines.containsAll(requested.keySet())) throw BusinessException.invalid();

                    List<CampaignItem> items = campaignItems.lockByIds(target.getCampaignId(),
                            refs.stream().map(ContributionItemRepository.LineRef::getCampaignItemId).toList());
                    Contribution contribution = contributions.lockById(contributionId)
                            .orElseThrow(BusinessException::missing);
                    // First load of these lines is the locked load, so cancelled_quantity is never stale.
                    List<ContributionItem> lines = contributionItems.lockByContribution(contributionId);
                    Map<UUID, Long> received = receivedFor(ownLines);

                    List<ContributionOutboxService.CancelledLine> cancelled = new ArrayList<>();
                    for (ContributionItem line : lines) {
                        Integer quantity = requested.get(line.getId());
                        if (quantity == null) continue;
                        // Throws 409 QUANTITY_EXCEEDED; the whole request rolls back, including earlier lines.
                        line.cancel(quantity, received.getOrDefault(line.getId(), 0L));
                        cancelled.add(new ContributionOutboxService.CancelledLine(
                                line.getId(), line.getCampaignItemId(), quantity));
                    }

                    long outstanding = lines.stream()
                            .mapToLong(line -> line.outstanding(received.getOrDefault(line.getId(), 0L))).sum();
                    long totalReceived = received.values().stream().mapToLong(Long::longValue).sum();
                    contribution.settleAfterCancellation(outstanding, totalReceived);
                    contributions.flush(); // surface CHECK violations before the outbox write

                    UUID auditEventId = outbox.enqueueReduction(target.getCaseId(), contribution, cancelled,
                            request.reason());

                    Map<UUID, CampaignItem> itemsById = items.stream()
                            .collect(Collectors.toMap(CampaignItem::getId, Function.identity()));
                    List<ContributionItem> ordered = lines.stream()
                            .sorted(Comparator.comparing(line -> itemsById.get(line.getCampaignItemId()), DISPLAY_ORDER))
                            .toList();
                    return new Result<>(
                            mapper.toResponse(contribution, ordered, received, auditEventId, PENDING_ON_CHAIN),
                            200, null, false);
                });
    }

    private Map<UUID, Long> receivedFor(Collection<UUID> contributionItemIds) {
        return contributionItemIds.isEmpty() ? Map.of() : toMap(contributionItems.receivedTotals(contributionItemIds));
    }

    /** ACTIVE account (401 otherwise) with an explicit donor grant (403 otherwise). No platform-role bypass. */
    private User requireDonor(Authentication auth) {
        User user = organizations.currentUser(auth);
        if (!user.isDonorEnabled()) {
            throw new AccessDeniedException("Access is denied");
        }
        return user;
    }

    private static Map<UUID, Long> toMap(List<ContributionItemRepository.ItemQuantity> rows) {
        return rows.stream().collect(Collectors.toMap(
                ContributionItemRepository.ItemQuantity::getItemId,
                ContributionItemRepository.ItemQuantity::getQuantity));
    }
}
