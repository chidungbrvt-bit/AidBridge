package com.aidbridge.blockchain.worker;

/** Common private audit and ownership context for goods milestones. */
public interface GoodsMilestoneSource {
    String getPayloadPrivate();
    String getOccurredAtUtc();
    String getOrganizationStatus();
    String getRelayerAddress();
    byte[] getOrgPublicRef();
    byte[] getCasePublicRef();
}
