package com.aidbridge.audit.service;

import com.aidbridge.audit.dto.response.BlockchainStatusResponse;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Translates blockchain_jobs + the selected blockchain_transactions attempt into the honest status shown on a
 * timeline (F05 section 4). Rules:
 * <ul>
 *   <li>The decision is driven by {@code job.status}, never by a prefix of {@code last_error}: codes such as
 *       RECEIPT_PENDING_OPERATOR_ATTENTION are written while the job is still SUBMITTED.</li>
 *   <li>CONFIRMED_ON_CHAIN only when the job is CONFIRMED and the attempt is CONFIRMED (receipt verified).</li>
 *   <li>A hash is shown only for a broadcast attempt (the query never returns PREPARED attempts).</li>
 *   <li>An explorer link only for chain 43113 and a well-formed 32-byte hash.</li>
 *   <li>Public view: no errorCode, and no hash/link when a failure is not a plain revert (the explorer would show
 *       "Success" for a mined transaction whose event did not match).</li>
 * </ul>
 */
@Component
public class AuditChainStatusMapper {

    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String PENDING_ON_CHAIN = "PENDING_ON_CHAIN";
    public static final String CONFIRMED_ON_CHAIN = "CONFIRMED_ON_CHAIN";
    public static final String FAILED_ON_CHAIN = "FAILED_ON_CHAIN";

    static final long FUJI_CHAIN_ID = 43113L;
    static final long LOCAL_HARDHAT_CHAIN_ID = 31337L;
    private static final Pattern TX_HASH = Pattern.compile("^0x[0-9a-fA-F]{64}$");

    /** Columns read by AuditEventRepository; kept separate so the rules can be unit tested without a database. */
    public record ChainInput(String jobStatus, String jobLastError, Long jobChainId, String txHash, String txStatus,
            Long txBlockNumber, Long txChainId, String txContractAddress) {
    }

    private final String explorerTxBaseUrl;

    public AuditChainStatusMapper(
            @Value("${aidbridge.blockchain.explorer-tx-base-url:https://testnet.snowtrace.io/tx/}") String baseUrl) {
        if (baseUrl == null || !baseUrl.startsWith("https://")) {
            throw new IllegalStateException("INVALID_EXPLORER_URL: aidbridge.blockchain.explorer-tx-base-url must use https");
        }
        this.explorerTxBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public BlockchainStatusResponse resolve(ChainInput in, boolean publicView) {
        if (in.jobStatus() == null) {
            return new BlockchainStatusResponse(NOT_APPLICABLE, null, null, null, null, null, null, null);
        }
        boolean broadcast = in.txStatus() != null && in.txHash() != null && TX_HASH.matcher(in.txHash()).matches();
        return switch (in.jobStatus()) {
            case "CONFIRMED" -> "CONFIRMED".equals(in.txStatus()) && broadcast
                    ? withTx(CONFIRMED_ON_CHAIN, in, true, null)
                    // Job says CONFIRMED but no confirmed attempt: never claim more than we can prove.
                    : pending(in, broadcast);
            case "FAILED" -> failed(in, broadcast, publicView);
            // PENDING, PROCESSING, SUBMITTED and any unknown value: still waiting, never confirmed.
            default -> pending(in, broadcast);
        };
    }

    private BlockchainStatusResponse pending(ChainInput in, boolean broadcast) {
        if (broadcast && "SUBMITTED".equals(in.txStatus())) {
            return withTx(PENDING_ON_CHAIN, in, false, null);
        }
        return withoutTx(PENDING_ON_CHAIN, in, null);
    }

    private BlockchainStatusResponse failed(ChainInput in, boolean broadcast, boolean publicView) {
        String errorCode = publicView ? null : in.jobLastError();
        boolean reverted = "TX_REVERTED".equals(in.jobLastError()) && "REVERTED".equals(in.txStatus());
        if (broadcast && reverted) {
            return withTx(FAILED_ON_CHAIN, in, true, errorCode);
        }
        if (broadcast && !publicView) {
            // Internal operators need the hash to investigate a receipt mismatch.
            return withTx(FAILED_ON_CHAIN, in, in.txBlockNumber() != null, errorCode);
        }
        return withoutTx(FAILED_ON_CHAIN, in, errorCode);
    }

    private BlockchainStatusResponse withTx(String status, ChainInput in, boolean showBlock, String errorCode) {
        long chainId = in.txChainId() != null ? in.txChainId() : in.jobChainId();
        String link = chainId == FUJI_CHAIN_ID ? explorerTxBaseUrl + in.txHash() : null;
        return new BlockchainStatusResponse(status, chainId, network(chainId), in.txContractAddress(), in.txHash(),
                showBlock ? in.txBlockNumber() : null, link, errorCode);
    }

    private BlockchainStatusResponse withoutTx(String status, ChainInput in, String errorCode) {
        Long chainId = in.jobChainId();
        return new BlockchainStatusResponse(status, chainId, chainId == null ? null : network(chainId), null, null,
                null, null, errorCode);
    }

    static String network(long chainId) {
        if (chainId == FUJI_CHAIN_ID) return "Avalanche Fuji testnet";
        if (chainId == LOCAL_HARDHAT_CHAIN_ID) return "Local Hardhat";
        return null;
    }
}
