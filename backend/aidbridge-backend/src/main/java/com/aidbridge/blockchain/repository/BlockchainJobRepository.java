package com.aidbridge.blockchain.repository;

import com.aidbridge.blockchain.entity.BlockchainJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockchainJobRepository extends JpaRepository<BlockchainJob, UUID> {

    /**
     * Highest chain sequence already assigned to a case (0 if none). MAX+1 is race-free only while the
     * caller holds the case row lock; UNIQUE (aid_case_id, chain_sequence) backs that up in the database.
     */
    @Query("SELECT COALESCE(MAX(j.chainSequence), 0) FROM BlockchainJob j WHERE j.caseId = :caseId")
    long maxChainSequence(@Param("caseId") UUID caseId);
}
