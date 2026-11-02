package com.ecommerce.order.repository;

import com.ecommerce.order.entity.ProcessedOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {
    boolean existsByOrderIdAndEventType(Long orderId, String eventType);
    Optional<ProcessedOrderEvent> findByOrderIdAndEventType(Long orderId, String eventType);
}

