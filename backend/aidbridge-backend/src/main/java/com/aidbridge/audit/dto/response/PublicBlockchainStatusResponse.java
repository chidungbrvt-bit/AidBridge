package com.aidbridge.audit.dto.response;

/**
 * Public chain block (T02). Deliberately has no errorCode field, so a worker reason code cannot be serialized
 * even by mistake. txHash/explorerUrl are null for failures that are not a plain revert.
 */
public record PublicBlockchainStatusResponse(
        String status,
        Long chainId,
        String network,
        String contractAddress,
        String txHash,
        Long blockNumber,
        String explorerUrl) {

    public static PublicBlockchainStatusResponse from(BlockchainStatusResponse resolved) {
        return new PublicBlockchainStatusResponse(resolved.status(), resolved.chainId(), resolved.network(),
                resolved.contractAddress(), resolved.txHash(), resolved.blockNumber(), resolved.explorerUrl());
    }
}
