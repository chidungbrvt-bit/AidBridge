package com.aidbridge.contribution.mapper;

import com.aidbridge.contribution.dto.response.CommitmentResponse;
import com.aidbridge.contribution.entity.Contribution;
import com.aidbridge.contribution.entity.ContributionItem;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ContributionMapper {

    /** Live outbox job state -> public ChainStatus (docs/fixtures/f02/goods-flow.types.ts). */
    public static String chainStatus(String jobStatus) {
        if (jobStatus == null) {
            return "NOT_APPLICABLE";
        }
        return switch (jobStatus) {
            case "CONFIRMED" -> "CONFIRMED_ON_CHAIN";
            case "FAILED" -> "FAILED_ON_CHAIN";
            default -> "PENDING_ON_CHAIN"; // PENDING, PROCESSING, SUBMITTED
        };
    }

    /** Lines must already be in display order; receivedByLine is keyed by contribution item ID. */
    public CommitmentResponse toResponse(
            Contribution contribution,
            List<ContributionItem> orderedLines,
            Map<UUID, Long> receivedByLine,
            UUID auditEventId,
            String blockchainStatus
    ) {
        var items = orderedLines.stream().map(line -> {
            int received = Math.toIntExact(receivedByLine.getOrDefault(line.getId(), 0L));
            return new CommitmentResponse.Item(
                    line.getId(),
                    line.getCampaignItemId(),
                    line.getPledgedQuantity(),
                    line.getCancelledQuantity(),
                    received,
                    line.getPledgedQuantity() - line.getCancelledQuantity() - received);
        }).toList();

        return new CommitmentResponse(
                contribution.getId(),
                contribution.getCampaign().getId(),
                items,
                contribution.getStatus(),
                // PostgreSQL stores microseconds; truncate so G01 and later G02 reads report the same instant.
                contribution.getPledgedAt().truncatedTo(ChronoUnit.MICROS),
                auditEventId,
                blockchainStatus);
    }
}
