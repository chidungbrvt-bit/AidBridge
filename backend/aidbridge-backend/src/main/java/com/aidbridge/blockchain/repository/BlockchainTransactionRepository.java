package com.aidbridge.blockchain.repository;

import com.aidbridge.blockchain.entity.BlockchainTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, UUID> {

    List<BlockchainTransaction> findByJob_IdOrderByPreparedAtAsc(UUID jobId);

    Optional<BlockchainTransaction> findByTxHash(String txHash);

    /** Highest journaled nonce (including mined attempts; never reuse a nonce after a stale RPC) for this signer (null when none). */
    @Query("""
            SELECT MAX(t.nonce) FROM BlockchainTransaction t
            WHERE t.chainId = :chainId AND LOWER(t.signerAddress) = LOWER(:signer)
            """)
    Long maxReservedNonce(@Param("chainId") long chainId, @Param("signer") String signer);
}
