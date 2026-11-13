package com.aidbridge.contribution.repository;

import com.aidbridge.contribution.entity.GoodsReceiptItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, UUID> {
}
