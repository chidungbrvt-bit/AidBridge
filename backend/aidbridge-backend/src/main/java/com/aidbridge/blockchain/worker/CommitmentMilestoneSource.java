package com.aidbridge.blockchain.worker;

import java.util.UUID;

/** Private audit snapshot and immutable facts for a pledge, reduction, or receipt. */
public interface CommitmentMilestoneSource extends GoodsMilestoneSource {
    UUID getContributionId();
    String getPledgedLines();
    UUID getReceiptId();
    String getReceivedAtUtc();
    String getReceivedLines();
    byte[] getCommitmentRef();
}
