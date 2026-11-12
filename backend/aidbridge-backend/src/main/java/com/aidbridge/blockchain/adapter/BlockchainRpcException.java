package com.aidbridge.blockchain.adapter;

/** RPC failure or JSON-RPC error. {@code code} is a stable machine-readable reason; never contains secrets. */
public class BlockchainRpcException extends RuntimeException {

    private final String code;

    public BlockchainRpcException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public BlockchainRpcException(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
