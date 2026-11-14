package com.aidbridge.audit.dto.response;

/**
 * Chain block of one audit event (API_SPECIFICATION AuditEventResponse.blockchain). Optional fields are null
 * when there is nothing true to show: no txHash before broadcast, no blockNumber/explorerUrl without a mined
 * receipt or a Fuji hash. {@code errorCode} is a stable worker reason code, internal views only.
 */
public record BlockchainStatusResponse(
        String status,
        Long chainId,
        String network,
        String contractAddress,
        String txHash,
        Long blockNumber,
        String explorerUrl,
        String errorCode) {
}
