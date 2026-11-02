package com.ecommerce.notification.dto;

import com.ecommerce.notification.entity.NotificationLog;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String eventId,
        Long orderId,
        Long customerId,
        String recipientEmail,
        String eventType,
        String subject,
        String message,
        String correlationId,
        String status,
        Instant createdAt
) {
    public static NotificationResponse fromEntity(NotificationLog log) {
        return new NotificationResponse(
                log.getId(),
                log.getEventId(),
                log.getOrderId(),
                log.getCustomerId(),
                log.getRecipientEmail(),
                log.getType() != null ? log.getType().name() : null,
                log.getSubject(),
                log.getMessage(),
                log.getCorrelationId(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }
}
