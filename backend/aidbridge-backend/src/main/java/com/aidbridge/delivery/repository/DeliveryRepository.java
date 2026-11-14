package com.aidbridge.delivery.repository;

import com.aidbridge.delivery.entity.Delivery;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
}