package com.ecommerce.notification.repository;

import com.ecommerce.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationLog, Long> {

    boolean existsByEventId(String eventId);

    List<NotificationLog> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Optional<NotificationLog> findFirstByOrderIdAndCustomerIdIsNotNull(Long orderId);

    boolean existsByOrderId(Long orderId);
}
