package com.aidbridge.blockchain.worker;

import java.util.UUID;

/** Private delivery audit and its immutable V15/V16 records. */
public interface DeliveryMilestoneSource extends GoodsMilestoneSource {
    UUID getDeliveryId();
    UUID getCampaignId();
    byte[] getDeliveryRef();
    String getHandedOverAtUtc();
    String getDeliveredLines();
    String getEvidenceVersions();
}
