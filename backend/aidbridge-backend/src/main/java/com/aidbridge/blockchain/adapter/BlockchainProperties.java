package com.aidbridge.blockchain.adapter;

import java.math.BigInteger;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chain connection settings ({@code aidbridge.blockchain.*}). Disabled by default; when disabled no
 * blockchain bean is created. The relayer key comes from the environment only and is never printed.
 */
@ConfigurationProperties(prefix = "aidbridge.blockchain")
public record BlockchainProperties(
        boolean enabled,
        String rpcUrl,
        long chainId,
        String registryAddress,
        String relayerPrivateKey,
        BigInteger gasLimit,
        BigInteger maxGasPriceWei) {

    private static final Pattern ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final Pattern PRIVATE_KEY = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    public static final long FUJI_CHAIN_ID = 43113L;
    public static final long LOCAL_HARDHAT_CHAIN_ID = 31337L;

    public BlockchainProperties {
        if (gasLimit == null) {
            gasLimit = BigInteger.valueOf(300_000);
        }
        if (maxGasPriceWei == null) {
            maxGasPriceWei = BigInteger.valueOf(25_000_000_000L); // 25 gwei cap, same as the C04 runner
        }
    }

    /** Fails fast on a misconfigured enabled environment; never echoes the key. */
    public void validate() {
        if (rpcUrl == null || rpcUrl.isBlank()) {
            throw new IllegalStateException("MISSING_RPC_URL: aidbridge.blockchain.rpc-url is required");
        }
        if (chainId != FUJI_CHAIN_ID && chainId != LOCAL_HARDHAT_CHAIN_ID) {
            throw new IllegalStateException("INVALID_CHAIN_ID: only 43113 (Fuji) or 31337 (local Hardhat) are allowed");
        }
        if (chainId == FUJI_CHAIN_ID && !rpcUrl.startsWith("https://")) {
            throw new IllegalStateException("MISSING_RPC_URL: Fuji requires an HTTPS RPC endpoint");
        }
        if (registryAddress == null || !ADDRESS.matcher(registryAddress).matches()) {
            throw new IllegalStateException("INVALID_REGISTRY_ADDRESS: expected a 20-byte hex address");
        }
        if (relayerPrivateKey == null || !PRIVATE_KEY.matcher(relayerPrivateKey).matches()) {
            throw new IllegalStateException("MISSING_OR_INVALID_SECRET: relayer private key must be a 32-byte hex string");
        }
        if (gasLimit.signum() <= 0 || maxGasPriceWei.signum() <= 0) {
            throw new IllegalStateException("INVALID_GAS_SETTINGS: gas limit and gas price cap must be positive");
        }
    }

    @Override
    public String toString() {
        return "BlockchainProperties[enabled=" + enabled + ", rpcUrl=" + rpcUrl + ", chainId=" + chainId
                + ", registryAddress=" + registryAddress + ", relayerPrivateKey=<redacted>, gasLimit=" + gasLimit
                + ", maxGasPriceWei=" + maxGasPriceWei + "]";
    }
}
