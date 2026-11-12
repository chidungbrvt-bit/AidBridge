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

    /** Highest nonce held by an attempt that may still be pending for this signer (null when none). */
    @Query("""
            SELECT MAX(t.nonce) FROM BlockchainTransaction t
            WHERE t.chainId = :chainId AND LOWER(t.signerAddress) = LOWER(:signer)
              AND t.status IN ('PREPARED', 'SUBMITTED')
            """)
    Long maxOpenNonce(@Param("chainId") long chainId, @Param("signer") String signer);
}
