package com.aidbridge.contribution.service;

import com.aidbridge.audit.entity.AuditEvent;
import com.aidbridge.audit.repository.AuditEventRepository;
import com.aidbridge.blockchain.entity.BlockchainJob;
import com.aidbridge.blockchain.repository.BlockchainJobRepository;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.entity.ContributionItem;
import com.aidbridge.contribution.entity.GoodsReceipt;
import com.aidbridge.shared.util.ChainRefs;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Contribution milestones for the transactional outbox. Same pattern as VerificationOutboxService. */
@Service
@RequiredArgsConstructor
public class ContributionOutboxService {
    static final String ENTITY_TYPE = "CONTRIBUTION";

    private final AuditEventRepository events;
    private final BlockchainJobRepository jobs;
    private final ObjectMapper json;

    /**
     * Writes AID_PLEDGED and a PENDING job in the caller's business transaction. The caller must hold
     * the case row lock: that lock is what makes MAX(chain_sequence)+1 race-free.
     * One pledge = one event, so the contribution ID is the event key.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueuePledge(UUID caseId, Contribution contribution, List<ContributionItem> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("contributionId", contribution.getId());
        payload.put("lines", lines.stream().map(line -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("campaignItemId", line.getCampaignItemId());
            entry.put("quantity", line.getPledgedQuantity());
            return entry;
        }).toList());

        long nextSequence = jobs.maxChainSequence(caseId) + 1;
        var event = events.save(new AuditEvent(contribution.getOrganizationId(), caseId,
                contribution.getDonorUserId(), ENTITY_TYPE, contribution.getId(), "AID_PLEDGED",
                contribution.getId(), json.writeValueAsString(payload)));
        jobs.saveAndFlush(new BlockchainJob(event, nextSequence));
        return event.getId();
    }

    /** One cancelled line as recorded in the private audit payload. */
    public record CancelledLine(UUID contributionItemId, UUID campaignItemId, int quantity) {
    }

    public record ReceivedLine(UUID contributionItemId, UUID campaignItemId, int quantity) {
    }

    /** Caller holds the case lock. Persist the receipt reference with the immutable event for C10. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueReceipt(UUID caseId, Contribution contribution, GoodsReceipt receipt, List<ReceivedLine> lines) {
        var hex = HexFormat.of();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("receiptId", receipt.getId());
        payload.put("contributionId", contribution.getId());
        payload.put("receiptRef", "0x" + hex.formatHex(ChainRefs.randomBytes32()));
        payload.put("commitmentRef", "0x" + hex.formatHex(contribution.getPublicRef()));
        payload.put("receivedAt", receipt.getReceivedAt());
        payload.put("lines", lines);

        long nextSequence = jobs.maxChainSequence(caseId) + 1;
        var event = events.save(new AuditEvent(contribution.getOrganizationId(), caseId,
                receipt.getRecorderId(), "GOODS_RECEIPT", receipt.getId(), "GOODS_RECEIVED",
                receipt.getId(), json.writeValueAsString(payload)));
        jobs.saveAndFlush(new BlockchainJob(event, nextSequence));
        return event.getId();
    }

    /**
     * Writes COMMITMENT_REDUCED and a PENDING job. A contribution can be reduced many times and has no
     * cancellation table, so the event key is a fresh UUID. Retries stay safe: the idempotency journal
     * replays the stored response, and a rolled-back attempt rolls this event back with it.
     * The caller must hold the case row lock (chain_sequence).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueueReduction(UUID caseId, Contribution contribution, List<CancelledLine> lines, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("payloadVersion", 1);
        payload.put("contributionId", contribution.getId());
        payload.put("lines", lines.stream().map(line -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("contributionItemId", line.contributionItemId());
            entry.put("campaignItemId", line.campaignItemId());
            entry.put("quantity", line.quantity());
            return entry;
        }).toList());
        payload.put("reason", reason);

        long nextSequence = jobs.maxChainSequence(caseId) + 1;
        var event = events.save(new AuditEvent(contribution.getOrganizationId(), caseId,
                contribution.getDonorUserId(), ENTITY_TYPE, contribution.getId(), "COMMITMENT_REDUCED",
                UUID.randomUUID(), json.writeValueAsString(payload)));
        jobs.saveAndFlush(new BlockchainJob(event, nextSequence));
        return event.getId();
    }
}
