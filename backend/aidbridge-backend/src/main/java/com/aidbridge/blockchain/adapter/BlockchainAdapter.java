package com.aidbridge.blockchain.adapter;

import java.math.BigInteger;
import java.util.Optional;

/**
 * The only gateway from the backend to the chain (BLOCKCHAIN_ARCHITECTURE: business modules write
 * events/jobs and never call Web3j). Implementations must not hold SQL transactions and must not log keys.
 */
public interface BlockchainAdapter {

    /** chainId reported by the RPC node; callers compare it with the configured chain before signing. */
    long readChainId();

    /** Address of the configured relayer key (lower-case 0x hex). */
    String relayerAddress();

    BigInteger pendingNonce(String address);

    BigInteger gasPrice();

    /** Signs locally (EIP-155) without any network call; the hash is computed before broadcast. */
    SignedTransaction signTransaction(String to, String calldata, BigInteger nonce, BigInteger gasPrice, BigInteger gasLimit);

    /** Broadcasts exact signed bytes; returns the node's transaction hash. */
    String broadcastRawTransaction(String rawTransactionHex);

    /** Empty while the transaction is not yet mined (or unknown to the node). */
    Optional<ChainReceipt> getReceipt(String transactionHash);

    /** Hash of the canonical block at {@code blockNumber}, used to reject receipts from re-orged blocks. */
    Optional<String> canonicalBlockHash(BigInteger blockNumber);
}
