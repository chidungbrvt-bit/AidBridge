package com.aidbridge.blockchain.adapter;

import java.math.BigInteger;

/** Exact signed bytes and locally computed hash, persisted before broadcast so retries reuse them. */
public record SignedTransaction(
        String rawTransactionHex,
        String transactionHash,
        String from,
        String to,
        BigInteger nonce,
        BigInteger gasPrice,
        BigInteger gasLimit,
        long chainId) {
}
