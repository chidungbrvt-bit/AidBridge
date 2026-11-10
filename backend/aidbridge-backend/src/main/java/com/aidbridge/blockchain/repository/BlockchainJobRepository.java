package com.aidbridge.blockchain.repository;

import com.aidbridge.blockchain.entity.BlockchainJob;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainJobRepository extends JpaRepository<BlockchainJob, UUID> {
}
