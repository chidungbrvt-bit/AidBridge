package com.aidbridge.blockchain.adapter;

import java.math.BigInteger;
import java.util.List;

/** Minimal receipt projection; status is 1 for success and 0 for revert. */
public record ChainReceipt(
        String transactionHash,
        int status,
        BigInteger blockNumber,
        String blockHash,
        String from,
        String to,
        List<ChainLog> logs) {

    public ChainReceipt {
        logs = List.copyOf(logs);
    }

    public record ChainLog(String address, List<String> topics, String data, BigInteger logIndex, boolean removed) {
        public ChainLog {
            topics = List.copyOf(topics);
        }
    }
}
