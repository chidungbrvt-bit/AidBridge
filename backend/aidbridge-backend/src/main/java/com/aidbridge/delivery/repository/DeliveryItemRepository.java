package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.DeliveryItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryItemRepository extends JpaRepository<DeliveryItem, UUID> {
}