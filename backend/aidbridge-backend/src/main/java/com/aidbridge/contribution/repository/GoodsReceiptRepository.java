package com.aidbridge.contribution.repository;

import com.aidbridge.contribution.entity.GoodsReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, UUID> {
}
