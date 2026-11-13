package com.aidbridge.blockchain.adapter;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

/** Web3j implementation. Signs legacy EIP-155 transactions (supported by Fuji C-Chain and Hardhat). */
public class Web3jBlockchainAdapter implements BlockchainAdapter {

    private final Web3j web3j;
    private final long chainId;
    private final Credentials credentials;

    public Web3jBlockchainAdapter(Web3j web3j, BlockchainProperties properties) {
        this.web3j = web3j;
        this.chainId = properties.chainId();
        this.credentials = Credentials.create(properties.relayerPrivateKey());
    }

    @Override
    public long readChainId() {
        return send(() -> web3j.ethChainId().send(), "eth_chainId").getChainId().longValueExact();
    }

    @Override
    public String relayerAddress() {
        return credentials.getAddress().toLowerCase();
    }

    @Override
    public BigInteger pendingNonce(String address) {
        return send(() -> web3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING).send(),
                "eth_getTransactionCount").getTransactionCount();
    }

    @Override
    public BigInteger gasPrice() {
        return send(() -> web3j.ethGasPrice().send(), "eth_gasPrice").getGasPrice();
    }

    @Override
    public SignedTransaction signTransaction(String to, String calldata, BigInteger nonce, BigInteger gasPrice,
            BigInteger gasLimit) {
        RawTransaction raw = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, BigInteger.ZERO, calldata);
        byte[] signed = TransactionEncoder.signMessage(raw, chainId, credentials);
        return new SignedTransaction(
                Numeric.toHexString(signed),
                Numeric.toHexString(Hash.sha3(signed)),
                relayerAddress(),
                to.toLowerCase(),
                nonce,
                gasPrice,
                gasLimit,
                chainId);
    }

    @Override
    public String broadcastRawTransaction(String rawTransactionHex) {
        EthSendTransaction response;
        try {
            response = web3j.ethSendRawTransaction(rawTransactionHex).send();
        } catch (IOException e) {
            // Outcome unknown: the node may or may not have accepted the bytes. Callers reconcile by hash.
            throw new BlockchainRpcException("RPC_UNAVAILABLE", "eth_sendRawTransaction outcome unknown", e);
        }
        if (response.hasError()) {
            throw new BlockchainRpcException("RPC_REJECTED", rpcErrorMessage(response.getError()));
        }
        return response.getTransactionHash();
    }

    @Override
    public Optional<ChainReceipt> getReceipt(String transactionHash) {
        EthGetTransactionReceipt response = send(() -> web3j.ethGetTransactionReceipt(transactionHash).send(),
                "eth_getTransactionReceipt");
        return response.getTransactionReceipt().map(Web3jBlockchainAdapter::toReceipt);
    }

    @Override
    public Optional<String> canonicalBlockHash(BigInteger blockNumber) {
        EthBlock response = send(() -> web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send(),
                "eth_getBlockByNumber");
        return Optional.ofNullable(response.getBlock()).map(EthBlock.Block::getHash);
    }

    static ChainReceipt toReceipt(TransactionReceipt receipt) {
        String rawStatus = receipt.getStatus();
        if (!"0x0".equals(rawStatus) && !"0x1".equals(rawStatus)) {
            throw new BlockchainRpcException("RECEIPT_STATUS_INVALID", "Missing or invalid receipt status");
        }
        List<ChainReceipt.ChainLog> logs = receipt.getLogs().stream()
                .map(Web3jBlockchainAdapter::toLog)
                .toList();
        int status = "0x1".equals(rawStatus) ? 1 : 0;
        return new ChainReceipt(
                receipt.getTransactionHash(),
                status,
                receipt.getBlockNumber(),
                receipt.getBlockHash(),
                lower(receipt.getFrom()),
                lower(receipt.getTo()),
                logs);
    }

    private static ChainReceipt.ChainLog toLog(Log log) {
        return new ChainReceipt.ChainLog(lower(log.getAddress()), log.getTopics(), log.getData(), log.getLogIndex(),
                log.isRemoved());
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase();
    }

    private static String rpcErrorMessage(Response.Error error) {
        return error == null ? "unknown JSON-RPC error" : error.getCode() + " " + error.getMessage();
    }

    @FunctionalInterface
    private interface RpcCall<T> {
        T call() throws IOException;
    }

    private static <T extends Response<?>> T send(RpcCall<T> call, String method) {
        T response;
        try {
            response = call.call();
        } catch (IOException e) {
            throw new BlockchainRpcException("RPC_UNAVAILABLE", method + " failed", e);
        }
        if (response.hasError()) {
            throw new BlockchainRpcException("RPC_REJECTED", method + " " + rpcErrorMessage(response.getError()));
        }
        return response;
    }
}
