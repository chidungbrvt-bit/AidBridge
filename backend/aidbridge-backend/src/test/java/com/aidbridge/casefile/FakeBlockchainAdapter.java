package com.aidbridge.casefile;

import com.aidbridge.blockchain.adapter.BlockchainAdapter;
import com.aidbridge.blockchain.adapter.BlockchainProperties;
import com.aidbridge.blockchain.adapter.BlockchainRpcException;
import com.aidbridge.blockchain.adapter.ChainReceipt;
import com.aidbridge.blockchain.adapter.SignedTransaction;
import com.aidbridge.blockchain.adapter.Web3jBlockchainAdapter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

/** Test double: real local signing (Web3j, offline), scripted node behaviour, records every broadcast. */
class FakeBlockchainAdapter implements BlockchainAdapter {

    private final Web3jBlockchainAdapter signer;
    final List<String> broadcasts = new ArrayList<>();
    Runnable beforeSign = () -> { };
    long chainId = 31337;
    BigInteger pendingNonce = BigInteger.ZERO;
    BigInteger gasPrice = BigInteger.valueOf(1_000_000_000L);
    BlockchainRpcException nextBroadcastFailure;
    BlockchainRpcException nextReceiptFailure;
    final Map<String, ChainReceipt> receipts = new HashMap<>();
    final Map<BigInteger, String> canonicalBlocks = new HashMap<>();

    FakeBlockchainAdapter(BlockchainProperties properties) {
        this.signer = new Web3jBlockchainAdapter(Web3j.build(new HttpService("http://127.0.0.1:1")), properties);
    }

    @Override public long readChainId() { return chainId; }
    @Override public String relayerAddress() { return signer.relayerAddress(); }
    @Override public BigInteger pendingNonce(String address) { return pendingNonce; }
    @Override public BigInteger gasPrice() { return gasPrice; }

    @Override
    public SignedTransaction signTransaction(String to, String calldata, BigInteger nonce, BigInteger price, BigInteger limit) {
        beforeSign.run();
        return signer.signTransaction(to, calldata, nonce, price, limit);
    }

    @Override
    public String broadcastRawTransaction(String rawTransactionHex) {
        broadcasts.add(rawTransactionHex);
        // Like a real node: once a nonce has been sent, the pending nonce moves past it.
        BigInteger sentNonce = org.web3j.crypto.TransactionDecoder.decode(rawTransactionHex).getNonce();
        if (sentNonce.add(BigInteger.ONE).compareTo(pendingNonce) > 0) {
            pendingNonce = sentNonce.add(BigInteger.ONE);
        }
        if (nextBroadcastFailure != null) {
            BlockchainRpcException failure = nextBroadcastFailure;
            nextBroadcastFailure = null;
            throw failure;
        }
        return Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(rawTransactionHex)));
    }

    @Override public Optional<ChainReceipt> getReceipt(String transactionHash) {
        if (nextReceiptFailure != null) {
            BlockchainRpcException failure = nextReceiptFailure;
            nextReceiptFailure = null;
            throw failure;
        }
        return Optional.ofNullable(receipts.get(transactionHash.toLowerCase()));
    }

    @Override public Optional<String> canonicalBlockHash(BigInteger blockNumber) {
        return Optional.ofNullable(canonicalBlocks.get(blockNumber));
    }

    /**
     * "Mines" a broadcast transaction the way AidRegistry would: one CaseVerified log built from the calldata
     * arguments. {@code eventIdTopicOverride} lets a test forge a wrong indexed eventId.
     */
    void mine(String rawTransactionHex, String registry, long blockNumber, String eventIdTopicOverride) {
        org.web3j.crypto.SignedRawTransaction tx =
                (org.web3j.crypto.SignedRawTransaction) org.web3j.crypto.TransactionDecoder.decode(rawTransactionHex);
        String args = Numeric.cleanHexPrefix(tx.getData()).substring(8); // drop selector
        String[] w = new String[6];
        for (int i = 0; i < 6; i++) {
            w[i] = "0x" + args.substring(i * 64, (i + 1) * 64);
        }
        String timestampWord = String.format("%064x", 1_762_650_000L);
        String data = "0x" + Numeric.cleanHexPrefix(w[3]) + Numeric.cleanHexPrefix(w[4]) + Numeric.cleanHexPrefix(w[5]) + timestampWord;
        String eventTopic = eventIdTopicOverride == null ? w[2] : eventIdTopicOverride;
        String hash = Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(rawTransactionHex)));
        String blockHash = "0x" + String.format("%064x", blockNumber);
        ChainReceipt.ChainLog log = new ChainReceipt.ChainLog(registry.toLowerCase(),
                List.of(com.aidbridge.blockchain.envelope.AidRegistryAbi.CASE_VERIFIED_TOPIC0, w[0], w[1], eventTopic),
                data, BigInteger.ZERO, false);
        receipts.put(hash, new ChainReceipt(hash, 1, BigInteger.valueOf(blockNumber), blockHash,
                relayerAddress(), registry.toLowerCase(), List.of(log)));
        canonicalBlocks.put(BigInteger.valueOf(blockNumber), blockHash);
    }
}
